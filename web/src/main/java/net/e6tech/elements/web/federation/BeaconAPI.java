package net.e6tech.elements.web.federation;

import net.e6tech.elements.common.federation.Member;

import javax.annotation.Nonnull;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.Collection;

@Path("/v1/beacon")
public class BeaconAPI {

    private CollectiveImpl collective;

    public CollectiveImpl getCollective() {
        return collective;
    }

    public void setCollective(CollectiveImpl collective) {
        this.collective = collective;
    }

    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("hosts")
    public Collection<Member> hosts() {
        return collective.getHostedMembers().values();
    }

    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("members")
    public Collection<Member> members() {
        return collective.members();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("events")
    public void onEvent(@Nonnull Event event) {
        collective.onEvent(event);
    }
}
