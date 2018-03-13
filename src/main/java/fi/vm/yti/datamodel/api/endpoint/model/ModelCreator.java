/*
 * Licensed under the European Union Public Licence (EUPL) V.1.1
 */
package fi.vm.yti.datamodel.api.endpoint.model;

import fi.vm.yti.datamodel.api.config.ApplicationProperties;
import fi.vm.yti.datamodel.api.service.EndpointServices;
import fi.vm.yti.datamodel.api.model.DataModel;
import fi.vm.yti.datamodel.api.model.ServiceCategory;
import fi.vm.yti.datamodel.api.service.*;
import fi.vm.yti.datamodel.api.utils.LDHelper;
import io.swagger.annotations.*;
import org.apache.jena.iri.IRI;
import org.apache.jena.iri.IRIException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

@Component
@Path("modelCreator")
@Api(tags = {"Model"}, description = "Construct new model template")
public class ModelCreator {

    private static final Logger logger = Logger.getLogger(ModelCreator.class.getName());

    private final JerseyResponseManager jerseyResponseManager;
    private final GraphManager graphManager;
    private final RHPOrganizationManager rhpOrganizationManager;
    private final JerseyClient jerseyClient;
    private final IDManager idManager;
    private final ServiceDescriptionManager serviceDescriptionManager;
    private final JenaClient jenaClient;
    private final EndpointServices endpointServices;
    private final ApplicationProperties applicationProperties;

    @Autowired
    ModelCreator(JerseyResponseManager jerseyResponseManager,
                 GraphManager graphManager,
                 RHPOrganizationManager rhpOrganizationManager,
                 JerseyClient jerseyClient,
                 IDManager idManager,
                 ServiceDescriptionManager serviceDescriptionManager,
                 JenaClient jenaClient,
                 EndpointServices endpointServices,
                 ApplicationProperties applicationProperties) {

        this.jerseyResponseManager = jerseyResponseManager;
        this.graphManager = graphManager;
        this.rhpOrganizationManager = rhpOrganizationManager;
        this.jerseyClient = jerseyClient;
        this.idManager = idManager;
        this.serviceDescriptionManager = serviceDescriptionManager;
        this.jenaClient = jenaClient;
        this.endpointServices = endpointServices;
        this.applicationProperties = applicationProperties;
    }

    @GET
    @Produces("application/ld+json")
    @ApiOperation(value = "Create new model", notes = "Create new model")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "New class is created"),
            @ApiResponse(code = 400, message = "Invalid ID supplied"),
            @ApiResponse(code = 403, message = "Invalid IRI in parameter"),
            @ApiResponse(code = 404, message = "Service not found"),
            @ApiResponse(code = 401, message = "No right to create new")})
    public Response newModel(
            @ApiParam(value = "Redirection service", required = false) @QueryParam("redirect") String redirect,
            @ApiParam(value = "Model prefix", required = true) @QueryParam("prefix") String prefix,
            @ApiParam(value = "Model label", required = true) @QueryParam("label") String label,
            @ApiParam(value = "Organization UUIDs", required = true)
            @QueryParam("orgList") String orgString,
            @ApiParam(value = "Service URIs", required = true)
            @QueryParam("serviceList") String servicesString,
            @ApiParam(value = "Label language", required = true, allowableValues="fi,en") @QueryParam("lang") String lang,
            @ApiParam(value = "Allowed languages as space list: 'en sv pl'. Default 'fi en'") @QueryParam("langList") String allowedLang) {

        logger.info(servicesString);

        List<String> serviceList = Arrays.asList(servicesString.split(" "));

        String[] orgs = orgString.split(" ");
        List<UUID> orgList = new ArrayList<>();

        for(int i = 0; i<orgs.length; i++) {
            orgList.add(UUID.fromString(orgs[i]));
        }

        if(!ServiceCategory.containsAll(serviceList)) {
            logger.info("no services");
            return jerseyResponseManager.invalidParameter();
        }

        if(allowedLang==null || allowedLang.equals("undefined") || allowedLang.length()==0) {
            allowedLang = "fi";
        }

        logger.info("prefix "+prefix);
        prefix = LDHelper.modelName(prefix);

        if(graphManager.isExistingPrefix(prefix)) {
            return jerseyResponseManager.usedIRI();
        }

        if(!rhpOrganizationManager.isExistingOrganization(orgList)) {
            logger.info("no org");
            return jerseyResponseManager.invalidParameter();
        }

        String namespace = applicationProperties.getDefaultNamespace()+prefix;

        IRI namespaceIRI;

        try {
            if (redirect!=null && !redirect.equals("undefined")) {
                if(redirect.endsWith("/")) {
                    namespaceIRI = idManager.constructIRI(redirect);
                } else if(redirect.endsWith("#")){
                    redirect=redirect.substring(0, redirect.length()-1);
                    namespaceIRI = idManager.constructIRI(redirect);
                } else {
                    namespaceIRI = idManager.constructIRI(redirect);
                }
            } else {
                namespaceIRI = idManager.constructIRI(namespace);
            }
        } catch (IRIException e) {
            logger.warning("INVALID: "+namespace);
            return jerseyResponseManager.invalidIRI();
        } catch (NullPointerException e) {
            return jerseyResponseManager.invalidParameter();
        }

        try {
            DataModel newModel = new DataModel(prefix, namespaceIRI, label, lang, allowedLang, serviceList, orgList, graphManager, serviceDescriptionManager, jenaClient, endpointServices);
            return jerseyClient.constructResponseFromGraph(newModel.asGraph());
        } catch (IllegalArgumentException ex) {
            return jerseyResponseManager.invalidParameter();
        }
    }
}
