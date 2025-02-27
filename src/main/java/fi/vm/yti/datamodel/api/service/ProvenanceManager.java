/*
 * Licensed under the European Union Public Licence (EUPL) V.1.1
 */
package fi.vm.yti.datamodel.api.service;

import fi.vm.yti.datamodel.api.config.ApplicationProperties;
import fi.vm.yti.datamodel.api.utils.LDHelper;

import org.apache.jena.iri.IRI;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.update.UpdateException;
import org.apache.jena.update.UpdateExecutionFactory;
import org.apache.jena.update.UpdateProcessor;
import org.apache.jena.update.UpdateRequest;
import org.apache.jena.vocabulary.DCTerms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ProvenanceManager {

    public static final Property generatedAtTime = ResourceFactory.createProperty("http://www.w3.org/ns/prov#", "generatedAtTime");
    private static final Logger logger = LoggerFactory.getLogger(ProvenanceManager.class);

    private final EndpointServices endpointServices;
    private final ApplicationProperties properties;
    private final JenaClient jenaClient;

    @Autowired
    ProvenanceManager(EndpointServices endpointServices,
                      ApplicationProperties properties,
                      JenaClient jenaClient) {
        this.endpointServices = endpointServices;
        this.properties = properties;
        this.jenaClient = jenaClient;
    }

    public boolean getProvMode() {
        return properties.isProvenance();
    }

    /**
     * Put model to provenance graph
     *
     * @param model Jena model
     * @param id    IRI of the graph as String
     */
    public void putToProvenanceGraph(Model model,
                                     String id) {
        jenaClient.putModelToProv(id, model);
    }

    /**
     * Creates Provenance activity for the given resource
     *
     * @param id       ID of the resource
     * @param model    Model containing the resource
     * @param provUUID Provenance UUID for the resource
     * @param user     UUID of the committing user
     */
    public void createProvenanceActivityFromModel(String id,
                                                  Model model,
                                                  String provUUID,
                                                  UUID user) {
        putToProvenanceGraph(model, provUUID);
        createProvenanceActivity(id, provUUID, user);
    }


    public String buildRemoveProvEntityQuery(String activityUri) {
        Model provModel = jenaClient.getModelFromProv(activityUri);
        String newQuery = "DROP SILENT GRAPH <" + activityUri + ">;\n";
        if(provModel!=null && provModel.size()>1) {
            NodeIterator previousVersionObjects = provModel.listObjectsOfProperty(LDHelper.curieToProperty("prov:generated"));
            while (previousVersionObjects.hasNext()) {
                String entityUri = previousVersionObjects.next().asResource().getURI();
                newQuery += "DROP SILENT GRAPH <" + entityUri + ">;\n";
            }
        } else {
            logger.warn("No provenance for "+activityUri);
        }
        return newQuery;
    }

    public String buildRemoveProvModelQuery(String modelId) {

        String newQuery = buildRemoveProvEntityQuery(modelId);

        Model hasPartGraph = jenaClient.getModelFromCore(modelId + "#HasPartGraph");
        if(hasPartGraph!=null && hasPartGraph.size()>1) {
            NodeIterator hasPartObjects = hasPartGraph.listObjectsOfProperty(DCTerms.hasPart);
            while (hasPartObjects.hasNext()) {
                String resUri = hasPartObjects.nextNode().asResource().toString();
                newQuery += buildRemoveProvEntityQuery(resUri);
            }
        } else {
            logger.warn("No #HasPart graph "+modelId);
        }

        return newQuery;
    }

    public void deleteProvenanceFromResource(String id) {
     deleteProvenance(id,buildRemoveProvEntityQuery(id));
    }

    public void deleteProvenanceFromModel(String id) {
        deleteProvenance(id,buildRemoveProvModelQuery(id));
    }

    public void deleteProvenance(String id, String query) {
        logger.info("Removing provenance from " + id);

        ParameterizedSparqlString pss = new ParameterizedSparqlString();
        pss.setCommandText(query);

        logger.debug(pss.toString());

        UpdateRequest queryObj = pss.asUpdate();
        UpdateProcessor qexec = UpdateExecutionFactory.createRemoteForm(queryObj, endpointServices.getProvSparqlUpdateAddress());

        try {
            qexec.execute();
        } catch (UpdateException ex) {
            logger.warn(ex.toString());
        }
    }

    public void createProvenanceActivityForNewVersionModel(String modelId, UUID user) {
        Model hasPartGraph = jenaClient.getModelFromCore(modelId+"#HasPartGraph");
        if(hasPartGraph!=null && hasPartGraph.size()>1) {
            NodeIterator hasPartObjects = hasPartGraph.listObjectsOfProperty(DCTerms.hasPart);
            while (hasPartObjects.hasNext()) {
                String resUri = hasPartObjects.nextNode().asResource().toString();
                if (resUri.startsWith(modelId + "#")) {
                    Model resourceModel = jenaClient.getModelFromCore(resUri);
                    createProvenanceActivityFromModel(resUri, resourceModel, "urn:uuid:" + UUID.randomUUID().toString(), user);
                }
            }
        }
    }

    /**
     * Returns query for creating the PROV Activity
     *
     * @param graph    ID of the resource
     * @param provUUID Provenance id of the resource
     * @param user     UUID of the committing user
     * @return UpdateRequest of the activity
     */

    public UpdateRequest createProvenanceActivityRequest(String graph,
                                                         String provUUID,
                                                         UUID user) {
        String query
            = "DELETE { "
            + "GRAPH ?graph {"
            + "?graph ?oldpredicate ?oldresource . "
            + "}"
            + "}"
            + "INSERT { "
            + "GRAPH ?graph { "
            + "?graph prov:startedAtTime ?creation . "
            + "?graph prov:generated ?jsonld . "
            + "?graph prov:used ?jsonld . "
            + "?graph a prov:Activity . "
            + "?graph rdfs:isDefinedBy ?modelGraph . "
            + "?graph prov:wasAttributedTo ?user . "
            + "?jsonld a prov:Entity . "
            + "?jsonld prov:wasAttributedTo ?user . "
            + "?jsonld prov:generatedAtTime ?creation . "
            + "}"
            + "GRAPH ?jsonld { "
            + "?graph a prov:Entity . "
            + "?graph dcterms:identifier ?versionID . }"
            + "}"
            + "WHERE { "
            + "BIND(now() as ?creation)"
            + "OPTIONAL { "
             + "GRAPH ?graph {"
             + "?graph ?oldpredicate ?oldresource . }"
             + "}"
            + "}";

        ParameterizedSparqlString pss = new ParameterizedSparqlString();
        pss.setNsPrefixes(LDHelper.PREFIX_MAP);

        pss.setIri("graph", graph);
        String modelGraph = graph.contains("#") ? graph.substring(0,graph.indexOf("#")) : graph;
        pss.setIri("modelGraph", modelGraph);
        pss.setIri("user", "urn:uuid:" + user.toString());
        pss.setIri("jsonld", provUUID);
        pss.setCommandText(query);
        return pss.asUpdate();
    }

    public void createProvenanceActivity(String graph,
                                         String provUUID,
                                         UUID user) {
        UpdateRequest queryObj = createProvenanceActivityRequest(graph, provUUID, user);
        jenaClient.updateToService(queryObj, endpointServices.getProvSparqlUpdateAddress());
    }

    public UpdateRequest createProvEntityRequest(String graph,
                                                 UUID user,
                                                 String provUUID) {
        String query
            = "DELETE { "
            + "GRAPH ?graph {"
            + "?graph prov:used ?oldEntity . "
            + "}"
            + "}"
            + "INSERT { "
            + "GRAPH ?graph { "
            + "?graph prov:generated ?jsonld . "
            + "?graph prov:used ?jsonld . "
            + "?jsonld a prov:Entity . "
            + "?jsonld prov:wasAttributedTo ?user . "
            + "?jsonld prov:generatedAtTime ?creation . "
            + "?jsonld prov:wasRevisionOf ?oldEntity . "
            + "}"
            + "GRAPH ?jsonld {"
            + "?graph a prov:Entity ."
            + "}"
            + "}"
            + "WHERE { "
            + "GRAPH ?graph { "
            + "?graph prov:used ?oldEntity . "
            + "}"
            + "BIND(now() as ?creation)"
            + "}";

        ParameterizedSparqlString pss = new ParameterizedSparqlString();
        pss.setNsPrefixes(LDHelper.PREFIX_MAP);

        pss.setIri("graph", graph);
        pss.setIri("user", "urn:uuid:" + user.toString());
        pss.setIri("jsonld", provUUID);
        pss.setCommandText(query);
        return pss.asUpdate();
    }

    public void createProvEntity(String graph,
                                 String provUUID,
                                 UUID user) {
        UpdateRequest queryObj = createProvEntityRequest(graph, user, provUUID);
        jenaClient.updateToService(queryObj, endpointServices.getProvSparqlUpdateAddress());
    }

    /**
     * Creates PROV Entities and renames ID:s if changed
     *
     * @param graph    Graph of the resource
     * @param model    Model containing the resource
     * @param user     UUID of the committing user
     * @param provUUID Provenance UUID for the resource
     * @param oldIdIRI Optional: Old IRI for the resource
     */
    public void createProvEntityBundle(String graph,
                                       Model model,
                                       UUID user,
                                       String provUUID,
                                       IRI oldIdIRI) {
        putToProvenanceGraph(model, provUUID);
        createProvEntity(graph, provUUID, user);
        if (oldIdIRI != null) {
            renameID(oldIdIRI.toString(), graph);
        }
    }

    /**
     * Query that renames ID:s in provenance service
     *
     * @param oldid Old id
     * @param newid New id
     * @return update request
     */
    public UpdateRequest renameIDRequest(String oldid,
                                         String newid) {

        String query
            = "INSERT { "
            + "GRAPH ?newid { "
            + "?newid prov:generated ?jsonld . "
            + "?newid prov:startedAtTime ?creation . "
            + "?newid prov:used ?any . "
            + "?newid a prov:Activity . "
            + "?newid prov:wasAttributedTo ?user . "
            + "?jsonld a prov:Entity . "
            + "?jsonld prov:wasAttributedTo ?user . "
            + "?jsonld prov:generatedAtTime ?creation . "
            + "?jsonld prov:wasRevisionOf ?oldEntity . "
            + "}}"
            + "WHERE { "
            + "GRAPH ?oldid { "
            + "?oldid prov:startedAtTime ?creation . "
            + "?oldid prov:generated ?jsonld . "
            + "?oldid prov:used ?any . "
            + "?oldid a prov:Activity . "
            + "?oldid prov:wasAttributedTo ?user . "
            + "?jsonld a prov:Entity . "
            + "?jsonld prov:wasAttributedTo ?user . "
            + "?jsonld prov:generatedAtTime ?creation . "
            + "OPTIONAL {?jsonld prov:wasRevisionOf ?oldEntity . }"
            + "}"
            + "}";

        ParameterizedSparqlString pss = new ParameterizedSparqlString();
        pss.setNsPrefixes(LDHelper.PREFIX_MAP);

        pss.setIri("oldid", oldid);
        pss.setIri("newid", newid);
        pss.setCommandText(query);

        return pss.asUpdate();

    }

    public void renameID(String oldid,
                         String newid) {
        UpdateRequest queryObj = renameIDRequest(oldid, newid);
        jenaClient.updateToService(queryObj, endpointServices.getProvSparqlUpdateAddress());
    }
}
