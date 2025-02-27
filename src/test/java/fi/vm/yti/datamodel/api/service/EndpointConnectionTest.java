package fi.vm.yti.datamodel.api.service;

import org.apache.jena.query.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.slf4j.Logger;import org.slf4j.LoggerFactory;
import org.springframework.test.context.junit4.SpringRunner;

import fi.vm.yti.datamodel.api.config.ApplicationProperties;
import static org.junit.Assert.fail;

@RunWith(SpringRunner.class)
@ActiveProfiles("junit")
@TestPropertySource("classpath:application-test.properties")
@SpringBootTest
public class EndpointConnectionTest {

    @Autowired
    private ApplicationProperties applicationProperties;

    private static final Logger logger = LoggerFactory.getLogger(EndpointConnectionTest.class.getName());

    @Test
    public void testEndpoint() {

        String queryString = "SELECT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 1";

        String endpoint = applicationProperties.getEndpoint()+"/core/sparql";

        logger.info("Testing "+endpoint);

        Query query = QueryFactory.create(queryString);
        try(QueryExecution qexec = QueryExecution.service(endpoint, query)) {
            ResultSet results = qexec.execSelect();
            while(results.hasNext()) {
                QuerySolution soln = results.nextSolution();
            }
        } catch(Exception ex) {
            fail("FAILED TO SEND: "+queryString+" to EDNPOINT: "+endpoint);
        }
    }
}
