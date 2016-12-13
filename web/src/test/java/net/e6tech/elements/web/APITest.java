package net.e6tech.elements.web;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by barry.
 */
@Path("/v1/setup")
public class APITest {

    private static List<DatabaseDriverDTO> databaseDrivers;

    static {
        List<DatabaseDriverDTO> list = new ArrayList<>();
        list.add(new DatabaseDriverDTO("MySQL/MariaDB"));
        list.add(new DatabaseDriverDTO("Oracle"));
        list.add(new DatabaseDriverDTO("Postgres"));
        databaseDrivers = list;
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON})
    @Path("drivers")
    public List<DatabaseDriverDTO> getSupportedDatabases() {
        System.out.println("getSupportedDatabases called");
        return databaseDrivers;
    }

    @POST
    @Consumes({MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_JSON})
    @Path("schema/create")
    public Response createSchema(CreateSchemaDTO dto) {
        System.out.println("createSchema called");
        return Response.status(Response.Status.CREATED).build();
    }

    public static class DatabaseDriverDTO {
        private String name;

        public DatabaseDriverDTO() {
        }

        public DatabaseDriverDTO(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class CreateSchemaDTO {
        private String name;

        public CreateSchemaDTO() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
