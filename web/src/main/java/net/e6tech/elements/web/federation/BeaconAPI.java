package net.e6tech.elements.web.federation;

import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.resources.Provision;

import javax.annotation.Nonnull;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.Collection;

@Path("/v1/beacon")
public class BeaconAPI {

    @Inject
    private Provision provision;

    private Federation federation;

    public Federation getFederation() {
        return federation;
    }

    public void setFederation(Federation federation) {
        this.federation = federation;
    }

    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("hosts")
    public Collection<Member> hosts() {
        return federation.getHostedMembers().values();
    }

    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("members")
    public Collection<Member> members() {
        return federation.members();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("events")
    public void onEvent(@Nonnull Event event) {
        federation.onEvent(event);
    }
}
