# PocketDeck R8 / ProGuard rules

# slf4j-api (transitive via junrar) probes StaticLoggerBinder at runtime when no SLF4J binding is present
-dontwarn org.slf4j.impl.StaticLoggerBinder
