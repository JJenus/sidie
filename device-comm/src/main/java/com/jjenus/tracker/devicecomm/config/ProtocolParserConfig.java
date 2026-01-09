@Configuration
public class ProtocolParserConfig {
    
    @Bean
    public List<ITrackerProtocolParser> protocolParsers() {
        return List.of(
            new GT06ProtocolParser(),
            new AutoseekerProtocolParser()
        );
    }
}