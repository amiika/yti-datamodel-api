/*
 * Licensed under the European Union Public Licence (EUPL) V.1.1 
 */
package com.csc.fi.ioapi.utils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFReader;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.WebResource.Builder;
import com.sun.jersey.api.uri.UriComponent;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import org.apache.jena.atlas.json.JSON;
import org.apache.jena.atlas.json.JsonObject;
import org.apache.jena.atlas.json.JsonValue;
import org.apache.jena.atlas.web.ContentType;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RiotException;
/**
 * 
 * @author malonen
 */
public class JerseyFusekiClient {
    
    static final private Logger logger = Logger.getLogger(JerseyFusekiClient.class.getName());
    
    /**
     * Returns JENA model from JSONLD Response
     * @param response  Response object
     * @return          Jena model parsed from Reponse entity or empty model
     */
    public static Model getJSONLDResponseAsJenaModel(Response response) {
         Model model = ModelFactory.createDefaultModel();
         
         try {
         RDFReader reader = model.getReader(Lang.JSONLD.getName());
         /* TODO: Base uri input? */
         reader.read(model, (InputStream)response.getEntity(), "http://example.org/");
         } catch(RiotException ex) {
             logger.info(ex.getMessage());
             return model;
         }
         
         return model;
        
    }
    
    /**
     *
     * @param resourceURI
     * @return
     */
    public static Model getResourceAsJenaModel(String resourceURI) {
        
        Client client = Client.create();
        WebResource webResource = client.resource(resourceURI);
        Builder builder = webResource.accept("text/turtle");
        ClientResponse response = builder.get(ClientResponse.class);
        
         Model model = ModelFactory.createDefaultModel();
         
         try {
         RDFReader reader = model.getReader(Lang.TURTLE.getName());
         reader.read(model, response.getEntityInputStream(), resourceURI);
         } catch(RiotException ex) {
             return model;
         }
         
         return model;
        
    }
    
    /**
     *
     * @param id
     * @param service
     * @return
     */
    public static JsonValue getGraphContextFromService(String id, String service) {
         
        Client client = Client.create();

        WebResource webResource = client.resource(service)
                                  .queryParam("graph", id);

        Builder builder = webResource.accept("application/ld+json");
        ClientResponse response = builder.get(ClientResponse.class);

        JsonObject json = JSON.parse(response.getEntityInputStream());
        JsonValue jsonContext = json.get("@context");
        
        return jsonContext;
        
       
    }
    
    /**
     *
     * @param id
     * @param service
     * @return
     */
    public static ClientResponse getGraphClientResponseFromService(String id, String service) {
                   
            Client client = Client.create();
            WebResource webResource = client.resource(service)
                                  .queryParam("graph", id);
           
            WebResource.Builder builder = webResource.accept("application/ld+json");
            return builder.get(ClientResponse.class);
    }
    
    /**
     *
     * @param id
     * @param service
     * @param ctype
     * @return
     */
    public static ClientResponse getGraphClientResponseFromService(String id, String service, String ctype) {
                   
            Client client = Client.create();
            WebResource webResource = client.resource(service)
                                  .queryParam("graph", id);
           
            WebResource.Builder builder = webResource.accept(ctype);
            return builder.get(ClientResponse.class);
    }
    
    /**
     *
     * @param id
     * @param service
     * @return
     */
    public static Response getGraphResponseFromService(String id, String service) {
        try {
        Client client = Client.create();

        WebResource webResource = client.resource(service)
                                  .queryParam("graph", id);

        Builder builder = webResource.accept("application/ld+json");
        ClientResponse response = builder.get(ClientResponse.class);

        if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
           logger.log(Level.INFO, response.getStatus()+" from SERVICE "+service+" and GRAPH "+id);
           return JerseyResponseManager.notFound();
        }

        ResponseBuilder rb = Response.status(response.getStatus()); 
        rb.entity(response.getEntityInputStream());

       return rb.build();
        } catch(ClientHandlerException ex) {
          logger.log(Level.WARNING, "Expect the unexpected!", ex);
          return JerseyResponseManager.unexpected();
        }
    }
    
    /**
     *
     * @param id
     * @param service
     * @param contentType
     * @param raw
     * @return
     */
    public static Response getGraphResponseFromService(String id, String service, ContentType contentType, boolean raw) {
        try {
        Client client = Client.create();

        WebResource webResource = client.resource(service)
                                  .queryParam("graph", id);

        Builder builder = webResource.accept(contentType.getContentType());
        ClientResponse response = builder.get(ClientResponse.class);

        if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
           logger.log(Level.INFO, response.getStatus()+" from SERVICE "+service+" and GRAPH "+id);
           return JerseyResponseManager.notFound();
        }

        ResponseBuilder rb = Response.status(response.getStatus()); 
        rb.entity(response.getEntityInputStream());
        
        if(!raw) {
            try {
                rb.type(contentType.getContentType());
            } catch(IllegalArgumentException ex) {
                 rb.type("text/plain;charset=utf-8");
            }
        } else {
            rb.type("text/plain;charset=utf-8");
        }

       return rb.build();
        } catch(ClientHandlerException ex) {
          logger.log(Level.WARNING, "Expect the unexpected!", ex);
          return JerseyResponseManager.unexpected();
        }
    }
    
    /**
     *
     * @param graph
     * @param body
     * @param service
     * @return
     */
    public static ClientResponse putGraphToTheService(String graph, String body, String service) {
        Client client = Client.create();
        WebResource webResource = client.resource(service).queryParam("graph", graph);
        WebResource.Builder builder = webResource.header("Content-type", "application/ld+json");
        return builder.put(ClientResponse.class, body);
    }
    
    
     /**
     *
     * @param graph
     * @param body
     * @param service
     * @return
     */
    public static boolean graphIsUpdatedToTheService(String graph, String body, String service) {
        Client client = Client.create();
        WebResource webResource = client.resource(service).queryParam("graph", graph);
        WebResource.Builder builder = webResource.header("Content-type", "application/ld+json");
        
        ClientResponse response = builder.put(ClientResponse.class, body);
        
        if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
               logger.log(Level.WARNING, "Unexpected: Model update failed: "+graph);
               return false;
        } else return true;
        
    }
    
    
    /**
     *
     * @param graph
     * @param body
     * @param service
     * @return
     */
    public static ClientResponse postGraphToTheService(String graph, String body, String service) {
        Client client = Client.create();
        WebResource webResource = client.resource(service).queryParam("graph", graph);
        WebResource.Builder builder = webResource.header("Content-type", "application/ld+json");
        return builder.post(ClientResponse.class, body);
    }
    
    /**
     *
     * @param query
     * @param service
     * @return
     */
    public static ClientResponse clientResponseFromConstruct(String query, String service) {
                   
            Client client = Client.create();
            WebResource webResource = client.resource(service)
                                      .queryParam("query", UriComponent.encode(query,UriComponent.Type.QUERY));
            WebResource.Builder builder = webResource.accept("application/ld+json");
            return builder.get(ClientResponse.class);

    }
    
    /**
     *
     * @param query
     * @param service
     * @param contentType
     * @return
     */
    public static ClientResponse clientResponseFromConstruct(String query, String service, ContentType contentType) {
                     
            Client client = Client.create();
            WebResource webResource = client.resource(service)
                                      .queryParam("query", UriComponent.encode(query,UriComponent.Type.QUERY));
            WebResource.Builder builder = webResource.accept(contentType.getContentType());
            return builder.get(ClientResponse.class);

    }
    
    /**
     *
     * @param query
     * @param service
     * @return
     */
    public static Response constructGraphFromService(String query, String service) {
        
            Client client = Client.create();

            WebResource webResource = client.resource(service)
                                      .queryParam("query", UriComponent.encode(query,UriComponent.Type.QUERY));

            WebResource.Builder builder = webResource.accept("application/ld+json");

            ClientResponse response = builder.get(ClientResponse.class);
            ResponseBuilder rb = Response.status(response.getStatus()); 
            rb.entity(response.getEntityInputStream());
            
            if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
               return JerseyResponseManager.unexpected(response.getStatus());
            }
            
           // rb.cacheControl(CacheControl.valueOf("no-cache"));
           // rb.header("Access-Control-Allow-Origin", "*");
	   // rb.header("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT");
           
            return rb.build();
            
    }
    
    /**
     *
     * @param query
     * @param fromService
     * @param toService
     * @param toGraph
     * @return
     */
    public static ClientResponse constructGraphFromServiceToService(String query, String fromService, String toService, String toGraph) {
        
            
            /* TODO: TEST! Not yet in use. */
            
            Client client = Client.create();

            WebResource webResource = client.resource(fromService)
                                      .queryParam("query", UriComponent.encode(query,UriComponent.Type.QUERY));

            WebResource.Builder builder = webResource.accept("application/ld+json");

            ClientResponse response = builder.get(ClientResponse.class);
            ResponseBuilder rb = Response.status(response.getStatus()); 
            rb.entity(response.getEntityInputStream());
            
            if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
              return builder.get(ClientResponse.class);
            }
            
            logger.info(rb.toString());
            
            return putGraphToTheService(toGraph, rb.toString(), toService);
            
    }
    
    /**
     *
     * @param graph
     * @param service
     * @return
     */
    public static Response deleteGraphFromService(String graph, String service) {
        
        try {

             Client client = Client.create();
             WebResource webResource = client.resource(service)
                                       .queryParam("graph", graph);

             WebResource.Builder builder = webResource.header("Content-type", "application/ld+json");
             ClientResponse response = builder.delete(ClientResponse.class);

             if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
                logger.log(Level.WARNING, "Database connection error: "+graph+" was not deleted from "+service+"! Status "+response.getStatus());
                return JerseyResponseManager.unexpected();
             }
             
             return JerseyResponseManager.okNoContent();

       } catch(Exception ex) {
            logger.log(Level.WARNING, "Expect the unexpected!", ex);
            return JerseyResponseManager.unexpected();
       }
        
        
    }
    
}
