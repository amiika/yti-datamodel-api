/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.csc.fi.ioapi.utils;

import com.csc.fi.ioapi.config.Endpoint;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.hp.hpl.jena.datatypes.xsd.XSDDatatype;
import com.hp.hpl.jena.query.ParameterizedSparqlString;
import com.hp.hpl.jena.update.UpdateExecutionFactory;
import com.hp.hpl.jena.update.UpdateProcessor;
import com.hp.hpl.jena.update.UpdateRequest;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author malonen
 */
public class ServiceDescriptionManager {
    
    final static SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");
    
    public static void updateGraphDescription(String service, String graph) {
        
        String timestamp = fmt.format(new Date());
        
        System.out.println(timestamp);
        
        String query =
                "WITH <urn:csc:iow:sd>"+
                "DELETE { "+
                " ?graph dcterms:modified ?date . "+
                "} "+
                "INSERT { "+
                " ?graph dcterms:modified ?timestamp "+
                "} WHERE {"+
                " ?service a sd:Service . "+
                " ?service sd:graphCollection ?graphCollection . "+
                " ?graphCollection sd:namedGraph ?graph . "+
                " ?graph sd:name ?graphName . "+
                " OPTIONAL {?graph dcterms:modified ?date . }"+
                "}";
       
        ParameterizedSparqlString pss = new ParameterizedSparqlString();
        pss.setNsPrefixes(LDHelper.PREFIX_MAP);

        pss.setIri("graphName", graph);
        pss.setLiteral("timestamp", timestamp,XSDDatatype.XSDdateTime);
        pss.setCommandText(query);

        UpdateRequest queryObj = pss.asUpdate();
        UpdateProcessor qexec=UpdateExecutionFactory.createRemoteForm(queryObj,service);
        qexec.execute();
      
    }
    
        public static void createGraphDescription(String service, String graph, String group) {
        
        String timestamp = fmt.format(new Date());
        
         String query = 
                "WITH <urn:csc:iow:sd>"+
                "INSERT { ?graphCollection sd:namedGraph _:graph . "+
                " _:graph a sd:NamedGraph . "+
                " _:graph sd:name ?graphName . "+
                " _:graph dcterms:created ?timestamp . "+
                 " _:graph dcterms:isPartOf ?group . "+
                "} WHERE {"+
                " ?service a sd:Service . "+
                " ?service sd:availableGraphs ?graphCollection . "+
                " ?graphCollection a sd:GraphCollection . "+
                " FILTER NOT EXISTS { "+
                " ?graphCollection sd:namedGraph ?graph . "+
                " ?graph sd:name ?graphName . "+
                "}}";
        
          
         
        ParameterizedSparqlString pss = new ParameterizedSparqlString();
        pss.setNsPrefixes(LDHelper.PREFIX_MAP);

        pss.setIri("graphName", graph);
        pss.setIri("group", group);
        pss.setLiteral("timestamp", timestamp,XSDDatatype.XSDdateTime);
        pss.setCommandText(query);

        Logger.getLogger(ServiceDescriptionManager.class.getName()).log(Level.WARNING, pss.toString());
        
        UpdateRequest queryObj = pss.asUpdate();
        UpdateProcessor qexec=UpdateExecutionFactory.createRemoteForm(queryObj,service);
        qexec.execute();
        
 
    }
        
    public static void deleteGraphDescription(String graph) {
        
        String query =
                "WITH <urn:csc:iow:sd> "+
                "DELETE { "+
                " ?graph ?p ?o "+
                "} WHERE {"+
                " ?service a sd:Service . "+
                " ?service sd:availableGraphs ?graphCollection . "+
                " ?graphCollection sd:namedGraph ?graph . "+
                " ?graph sd:name ?graphName . "+
                " ?graph ?p ?o "+
                "}";
       
        ParameterizedSparqlString pss = new ParameterizedSparqlString();
        pss.setNsPrefixes(LDHelper.PREFIX_MAP);

        pss.setIri("graphName", graph);
        pss.setCommandText(query);

         Logger.getLogger(ServiceDescriptionManager.class.getName()).log(Level.WARNING,"Removing "+graph);
        Logger.getLogger(ServiceDescriptionManager.class.getName()).log(Level.WARNING, pss.toString());
        
        UpdateRequest queryObj = pss.asUpdate();
        UpdateProcessor qexec=UpdateExecutionFactory.createRemoteForm(queryObj,Endpoint.getEndpoint()+"/search/update");
        qexec.execute();
        
      
    }
    
    
}
