package fi.vm.yti.datamodel.api.model;

import fi.vm.yti.datamodel.api.service.EndpointServices;
import fi.vm.yti.datamodel.api.service.*;
import fi.vm.yti.datamodel.api.utils.*;
import org.apache.jena.iri.IRI;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.util.SplitIRI;

import java.util.UUID;
import java.util.logging.Logger;

public class Shape extends AbstractShape {

    private static final Logger logger = Logger.getLogger(Shape.class.getName());

    public Shape(IRI shapeId,
                 GraphManager graphManager,
                 ServiceDescriptionManager serviceDescriptionManager,
                 JenaClient jenaClient,
                 ModelManager modelManager) {
        super(shapeId, graphManager, serviceDescriptionManager, jenaClient, modelManager);
    }

    public Shape(String jsonld,
                 GraphManager graphManager,
                 JenaClient jenaClient,
                 ModelManager modelManager,
                 RHPOrganizationManager rhpOrganizationManager,
                 ServiceDescriptionManager serviceDescriptionManager) {
        super(modelManager.createJenaModelFromJSONLDString(jsonld), graphManager, jenaClient, modelManager, rhpOrganizationManager, serviceDescriptionManager);
    }

    public Shape(IRI classIRI,
                 IRI shapeIRI,
                 IRI profileIRI,
                 GraphManager graphManager,
                 JenaClient jenaClient,
                 ModelManager modelManager,
                 EndpointServices endpointServices) {
        super(graphManager, jenaClient, modelManager);

        logger.info("Creating shape from "+classIRI.toString()+" to "+shapeIRI.toString());

        ParameterizedSparqlString pss = new ParameterizedSparqlString();
        pss.setNsPrefixes(LDHelper.PREFIX_MAP);

        String queryString;
        String service;

        /* Create Shape from Class */
        if(graphManager.isExistingServiceGraph(SplitIRI.namespace(classIRI.toString()))) {

            service = "core";
            queryString = "CONSTRUCT  { "
                    + "?shapeIRI owl:versionInfo ?draft . "
                    + "?shapeIRI dcterms:modified ?modified . "
                    + "?shapeIRI dcterms:created ?creation . "
                    + "?shapeIRI sh:scopeClass ?classIRI . "
                    + "?shapeIRI a sh:Shape . "
                    + "?shapeIRI rdfs:isDefinedBy ?model . "
                    + "?shapeIRI rdfs:label ?label . "
                    + "?shapeIRI rdfs:comment ?comment . "
                    + "?shapeIRI dcterms:subject ?concept . "
                    + "?concept skos:prefLabel ?prefLabel . "
                    + "?concept skos:definition ?definition . "
                    + "?concept skos:inScheme ?scheme . "
                    + "?scheme dcterms:title ?title . "
                    + "?scheme termed:id ?schemeId . "
                    + "?scheme termed:graph ?termedGraph . "
                    + "?concept termed:graph ?termedGraph . "
                    + "?termedGraph termed:id ?termedGraphId . "
                    + "?shapeIRI sh:property ?shapeuuid . "
                    + "?shapeuuid ?p ?o . "
                    + "} WHERE { "
                    + "BIND(now() as ?creation) "
                    + "BIND(now() as ?modified) "
                    + "GRAPH ?classIRI { "
                    + "?classIRI a rdfs:Class . "
                    + "?classIRI rdfs:label ?label . "
                    + "OPTIONAL { ?classIRI rdfs:comment ?comment . } "
                    + "OPTIONAL {{ "
                    + "?classIRI dcterms:subject ?concept . "
                    + "?concept skos:prefLabel ?prefLabel . "
                    + "?concept skos:definition ?definition . "
                    + "} UNION { "
                    + "?classIRI dcterms:subject ?concept . "
                    + "?concept skos:prefLabel ?prefLabel . "
                    + "?concept skos:definition ?definition . "
                    + "?concept skos:inScheme ?collection . "
                    + "?collection dcterms:title ?title . "
                    + "}}"
                    + "OPTIONAL {"
                    + "?classIRI sh:property ?property .  "
                    /* Todo: Issue 472 */
                    + "BIND(IRI(CONCAT(STR(?property),?shapePropertyID)) as ?shapeuuid)"
                    + "?property ?p ?o . "
                    + "}} "
                    + "}";

            pss.setLiteral("shapePropertyID", "-"+ UUID.randomUUID().toString());


        } else {
            /* Create Shape from external IMPORT */
            service = "imports";
            logger.info("Using ext query:");
            queryString = QueryLibrary.externalShapeQuery;
        }

        pss.setCommandText(queryString);
        pss.setIri("classIRI", classIRI);
        pss.setIri("model", profileIRI);
        pss.setIri("modelService", endpointServices.getLocalhostCoreSparqlAddress());
        pss.setLiteral("draft", "DRAFT");
        pss.setIri("shapeIRI",shapeIRI);

        this.graph = graphManager.constructModelFromService(pss.toString(), service);

    }

}
