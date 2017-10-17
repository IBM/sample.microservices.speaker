FROM websphere-liberty:microProfile
COPY server.xml /config/server.xml
RUN installUtility install --acceptLicense defaultServer
COPY target/microservice-speaker-1.0.0-SNAPSHOT.war /config/apps/speaker.war
