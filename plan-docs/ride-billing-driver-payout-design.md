# Ride Billing & Driver Payout — Design

This covers only the money-movement slice of the ride-hailing system: how a
rider gets charged, and how a driver gets paid. It assumes the rest of the
ride-hailing schema already exists (`users`, `drivers`, `rides`,
`payment_methods`) — see the main ride-hailing design doc for those.

This is **not** the subscription billing model. A subscription charges on a
recurring schedule independent of any single action; a ride charges once,
triggered by one event ending. Two different problems — this doc solves the
ride one.

---

## 1. The two money flows

```
RIDER → PLATFORM                    PLATFORM → DRIVER
(payments)                          (payouts)

rider's card                        driver_earnings
    │  hold                             │  accrues per ride
    ▼                                   ▼
authorization ──ride ends──► capture   batched on a schedule
    │                            │          │
    ▼                            ▼          ▼
fare_line_items            payments row   payouts row
(base/surge/tip/etc)      (charge record) (transfer record)
```

These are kept as separate tables and separate processes on purpose. A
rider's card can decline while the driver still completed the trip and is
still owed for it — one table can't represent both sides failing
independently, so don't try to model payments and payouts as mirror images
of each other.

---

## 2. Schema

### fare_line_items
| column | type | notes |
|---|---|---|
| id | PK | |
| ride_id | FK -> rides | |
| type | enum | `base`, `distance`, `time`, `surge`, `promo_discount`, `toll`, `tip`, `cancellation_fee`, `platform_fee` |
| amount | numeric | negative for discounts |

The fare is a *sum of these*, never a single stored number. Surge is
logged at the multiplier active when the ride was requested — not
recomputed later, so a fare stays reconstructable and disputable months
after the ride.

### payments (rider → platform)
| column | type | notes |
|---|---|---|
| id | PK | |
| ride_id | FK -> rides | |
| payment_method_id | FK -> payment_methods | |
| idempotency_key | string, unique | prevents double-charging on a retried capture request |
| gateway_transaction_id | string | |
| authorized_amount | numeric | the hold amount, at request time |
| captured_amount | numeric | the final amount actually charged, at completion |
| currency | char(3) | |
| status | enum | `authorized`, `captured`, `failed`, `refunded` |
| authorized_at | timestamp | |
| captured_at | timestamp, nullable | |

Splitting `authorized_amount` from `captured_amount` matters: the estimate
at request time and the real fare at completion are routinely different
(traffic, wait time, route change), and collapsing them into one `amount`
column loses the ability to see how far off the original estimate was.

### refunds
| column | type | notes |
|---|---|---|
| id | PK | |
| payment_id | FK -> payments | |
| amount | numeric | |
| reason | text | |
| status | enum | `requested`, `approved`, `processed`, `denied` |
| requested_by | enum | `rider`, `support`, `system` |
| created_at | timestamp | |

A refund is a new row, never a mutation of the original `payments` row. The
payment record stays a true account of what was charged at the time; the
refund is a separate, later, additive event.

### driver_earnings (per-ride ledger)
| column | type | notes |
|---|---|---|
| id | PK | |
| ride_id | FK -> rides | |
| driver_id | FK -> drivers | |
| gross_fare | numeric | rider's total fare for this ride |
| platform_commission | numeric | |
| net_earning | numeric | gross − commission |
| payout_id | FK -> payouts, nullable | null until batched into a payout |

### payouts (platform → driver)
| column | type | notes |
|---|---|---|
| id | PK | |
| driver_id | FK -> drivers | |
| period_start | timestamp | |
| period_end | timestamp | |
| amount | numeric | sum of `driver_earnings.net_earning` for the period |
| status | enum | `pending`, `in_transit`, `paid`, `failed`, `held_for_review` |
| gateway_transfer_id | string | |
| transferred_at | timestamp, nullable | |

---

## 3. Rider charge flow (procedure)

1. **Ride requested** — a fare estimate is shown, computed from `ride_types`
   base rates plus any active surge multiplier. Nothing is charged or held
   yet at this step.
2. **Authorization hold** — once a driver is matched, place a hold on the
   rider's `payment_method` for roughly the estimated amount. `payments`
   row created with `status = 'authorized'`, `authorized_amount` set.
   Holds exist because the final fare isn't known until the ride ends.
3. **Ride completes → fare finalized** — `fare_line_items` rows are written
   (base, distance, time, surge, tip, promo discount). This is the moment
   the real total exists.
4. **Capture** — charge the hold for the *finalized* total, not the
   original estimate. Update `payments.captured_amount`,
   `status = 'captured'`. If the final fare exceeds the original hold,
   most processors allow capturing somewhat over it before requiring a
   fresh authorization; if under, capture less and release the rest.
5. **Branch on result:**
   - **Succeeded** → immediately create the `driver_earnings` row for that
     ride (`gross_fare`, `platform_commission`, `net_earning`). This is
     what feeds the payout batch later.
   - **Failed** → don't retry inline. Flag the ride unpaid, run a
     background retry job with backoff (same pattern as delivery retries
     in the notification system), and if attempts are exhausted, apply a
     collections flag on the rider's account that can block future ride
     requests until resolved.

---

## 4. Driver payout flow (procedure)

1. **Accrual** — `driver_earnings` rows build up silently, one per
   completed ride, `payout_id` left null. No transfer happens at this
   point.
2. **Scheduled batch job** (daily or weekly, not per-ride) — sums all
   unbatched `driver_earnings` for a driver over the period, creates one
   `payouts` row with `status = 'pending'`, and sets `payout_id` on each
   included `driver_earnings` row. Batching is deliberate: transfers cost
   money and take time, and a driver's payout should be one stable,
   reviewable total rather than a stream of tiny transfers.
3. **Transfer initiated** — `status = 'in_transit'`, `gateway_transfer_id`
   recorded for reconciliation against the payment gateway's own records.
4. **Branch on result:**
   - **Succeeded** → `status = 'paid'`, `transferred_at` set.
   - **Failed** (bad bank details, closed account) → `status =
     'held_for_review'`. Critically, do **not** re-batch the underlying
     `driver_earnings` rows into the next period's payout — they're
     already linked to this `payout_id`, and re-batching would double-count
     them. Hold this specific payout until the driver corrects their
     payout details, then retry transferring the same `payouts` row.

---

## 5. Why this shape, in one place

- **Two tables, two processes, both directions of money** — `payments` and
  `payouts` never share a table, because a decline on one side and a
  successful ride on the other are both real, independent outcomes.
- **Line items, not a single fare number** — `fare_line_items` is what
  makes a fare auditable and disputable after the fact, same reasoning as
  invoice line items in a subscription system, applied to a one-off charge
  instead of a recurring one.
- **Authorized vs. captured amount, kept separate** — the estimate and the
  real charge are different numbers for a reason (the ride actually
  happened in between), and the schema should show that gap, not hide it.
- **Refunds and holds are additive, never mutations** — a `payments` row
  reflects what was charged at the time; anything that changes that later
  (`refunds`) is a new row layered on top, not an edit.
- **Payout batching is idempotent by construction** — once a
  `driver_earnings` row has a `payout_id`, it's spoken for. A failed
  transfer holds that specific payout for review instead of silently
  re-entering the earnings into a future batch, which is the concrete bug
  this design prevents.
