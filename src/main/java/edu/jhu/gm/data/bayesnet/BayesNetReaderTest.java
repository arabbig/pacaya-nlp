package edu.jhu.gm.data.bayesnet;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;

import edu.jhu.gm.model.DenseFactor;
import edu.jhu.gm.model.Factor;
import edu.jhu.gm.model.FactorGraph;

public class BayesNetReaderTest {

    public static final String cpdSimpleResource = "/edu/jhu/gm/data/cpd-simple.txt";
    public static final String networkSimpleResource = "/edu/jhu/gm/data/network-simple.txt";
    
    @Test
    public void testReadSimple() throws IOException {
        FactorGraph fg = readSimpleFg(false);
        
        assertEquals(2, fg.getNumFactors());
        assertEquals(3, fg.getNumVars());
    }

    public static FactorGraph readSimpleFg(boolean logDomain) throws IOException {
        InputStream cpdIs = BayesNetReaderTest.class.getResourceAsStream(cpdSimpleResource);
        InputStream networkIs = BayesNetReaderTest.class.getResourceAsStream(networkSimpleResource);
        
        BayesNetReader bnr = new BayesNetReader();
        FactorGraph fg = bnr.readBnAsFg(networkIs, cpdIs);
        if (logDomain) {
            for (Factor f : fg.getFactors()) {
                ((DenseFactor)f).convertRealToLog();
            }
        }
        return fg;
    }

}