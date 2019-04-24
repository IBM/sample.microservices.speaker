/*
 * Copyright 2016 Microprofile.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.microprofile.showcase.speaker.rest;


import static javax.ws.rs.core.MediaType.TEXT_PLAIN;

import java.net.URI;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Metered;
import org.eclipse.microprofile.metrics.annotation.Metric;
import org.eclipse.microprofile.metrics.annotation.Timed;

import io.microprofile.showcase.speaker.health.HealthCheckBean;
import io.microprofile.showcase.speaker.model.Speaker;
import io.microprofile.showcase.speaker.persistence.SpeakerDAO;

/**
 * The Speaker resource
 */
@ApplicationScoped
@Produces({MediaType.APPLICATION_JSON})
@Consumes(MediaType.APPLICATION_JSON)
@Path("/")
@Metered(name="io.microprofile.showcase.speaker.rest.ResourceSpeaker.Type.Metered",tags="app=speaker")
public class ResourceSpeaker {

    @Inject
    private SpeakerDAO speakerDAO;

    @Context
    private UriInfo uriInfo;
    private @Inject HealthCheckBean healthCheckBean;

    /*
    * if this flag is set to true in server.xml, then
    * it invokes methods that throws exceptions.
    */
    @Inject
    @ConfigProperty(name = "breaking.service.broken")
    private Provider<Boolean> isServiceBroken;

    @GET
    @Timed
    @Metric
    @Counted(name="io.microprofile.showcase.speaker.rest.monotonic.retrieveAll.absolute",monotonic = true,tags="app=speaker")
    @Bulkhead(value = 3)
    public Collection<Speaker> retrieveAll() {
        final Collection<Speaker> speakers = this.speakerDAO.getSpeakers();
        speakers.forEach(this::addHyperMedia);
        return speakers;
    }
    // For use as a k8s readinessProbe for this service
    @GET
    @Path("/nessProbe")
    @Produces(MediaType.TEXT_PLAIN)
    @Counted(monotonic = true,tags="app=speaker")
    public Response nessProbe() throws Exception {
        return Response.ok("speaker ready at " + Calendar.getInstance().getTime()).build();
    }

    @POST
    @Path("/add")
    @Counted(monotonic = true,tags="app=speaker")
    public Speaker add(final Speaker speaker) {
        return this.addHyperMedia(this.speakerDAO.persist(speaker));
    }

    @DELETE
    @Path("/remove/{id}")
    @Counted(monotonic = true,tags="app=speaker")
    public void remove(@PathParam("id") final String id) {
        this.speakerDAO.remove(id);
    }

    @PUT
    @Counted(monotonic = true,tags="app=speaker")
    @Path("/update")
    public Speaker update(final Speaker speaker) {
        return this.addHyperMedia(this.speakerDAO.update(speaker));
    }

    @GET
    @Path("/retrieve/{id}")
    @Counted(monotonic = true,tags="app=speaker")
    public Speaker retrieve(@PathParam("id") final String id) {
        return this.addHyperMedia(this.speakerDAO.getSpeaker(id).orElse(new Speaker()));
    }

    @GET
    @Path("/failingService")
    @Counted(monotonic = true,tags="app=speaker")
    @Fallback(fallbackMethod = "fallBackMethodForFailingService")
    public Speaker retrieveFailingService() {
        throw new RuntimeException("Retrieve service failed!");
    }

    /**
     * Method to fallback on when you receive run time errors
     * @return
     */
    private Speaker fallBackMethodForFailingService() {
        return new Speaker();
    }

    @GET
    @Path("/failingServiceWithoutAnnotation")
    @Counted(monotonic = true,tags="app=speaker")
    public Speaker retrieveFailingServiceWithoutAnnotation() {
        throw new RuntimeException("Service Failed!");
    }

    @PUT
    @Path("/search")
    @Counted(monotonic = true,tags="app=speaker")
    @Fallback(fallbackMethod="fallbackSearch")
    @CircuitBreaker(requestVolumeThreshold=2, failureRatio=0.50, delay=5000, successThreshold=2)
    public Set<Speaker> searchFailure(final Speaker speaker) {
        if (isServiceBroken.get()) {
            throw new RuntimeException("Breaking Service failed!");
        }
        final Set<Speaker> speakers = this.speakerDAO.find(speaker);
        return speakers;
    }

    public Set<Speaker> fallbackSearch(final Speaker speaker){
        return new HashSet<>();
    }

    
    @POST
    @Path("/updateHealthStatus")
    @Produces(TEXT_PLAIN)
    @Consumes(TEXT_PLAIN)
    @Counted(name="io.microprofile.showcase.speaker.rest.ResourceSpeaker.updateHealthStatus.monotonic.absolute",monotonic=true,absolute=true,tags="app=vote")
    public void updateHealthStatus(@QueryParam("isAppDown") Boolean isAppDown) {
    	healthCheckBean.setIsAppDown(isAppDown);
    }


    private Speaker addHyperMedia(final Speaker s) {

        if (null != s) {

            if (null != s.getId()) {
                s.getLinks().put("self", this.getUri(s, "retrieve"));
                s.getLinks().put("remove", this.getUri(s, "remove"));
                s.getLinks().put("update", this.getUri("update"));
            }

            s.getLinks().put("add", this.getUri("add"));
            s.getLinks().put("search", this.getUri("search"));
        }

        return s;
    }

    private URI getUri(final Speaker s, final String path) {
        return this.uriInfo.getBaseUriBuilder().path(ResourceSpeaker.class).path(ResourceSpeaker.class, path).build(s.getId());
    }

    private URI getUri(final String path) {
        return this.uriInfo.getBaseUriBuilder().path(ResourceSpeaker.class).build(path);
    }
}
