package edu.jhu.nlp.depparse;

import java.io.IOException;

import org.apache.commons.cli.ParseException;
import org.apache.log4j.Logger;

import edu.jhu.gm.feat.FeatureVector;
import edu.jhu.nlp.data.simple.AlphabetStore;
import edu.jhu.nlp.data.simple.AnnoSentence;
import edu.jhu.nlp.data.simple.AnnoSentenceCollection;
import edu.jhu.nlp.data.simple.AnnoSentenceReaderSpeedTest;
import edu.jhu.nlp.data.simple.IntAnnoSentence;
import edu.jhu.nlp.words.PrefixAnnotator;
import edu.jhu.prim.list.LongArrayList;
import edu.jhu.util.FeatureNames;
import edu.jhu.util.Timer;
import edu.jhu.util.hash.MurmurHash;

public class FastDepParseFeSpeedTest {
    
    private static final Logger log = Logger.getLogger(FastDepParseFeSpeedTest.class);
    private static final int featureHashMod = 1000000;
    
    /**
     * Speed test results.
     * 
     * Gilim SSD:
     *    w/o        s=2400 n=169795 Toks / sec: 14677.99
     *    w/Murmur   s=2400 n=169795 Toks / sec: 7094.89
     *    w/BitShift s=2400 n=169795 Toks / sec: 7590.63
     */
    //@Test
    public void testSpeed() throws ParseException, IOException {
        AnnoSentenceCollection sents = AnnoSentenceReaderSpeedTest.readPtbYmConllx();
        PrefixAnnotator.addPrefixes(sents);
        AlphabetStore store = new AlphabetStore(sents);
        
        int trials = 3;
        
        FeatureNames alphabet = new FeatureNames();
        
        Timer timer = new Timer();
        timer.start();
        int n=0;
        for (int trial = 0; trial < trials; trial++) {
            for (int s=0; s<sents.size(); s++) {
                AnnoSentence sent = sents.get(s);
                IntAnnoSentence isent = new IntAnnoSentence(sent, store);
                for (int i = -1; i < sent.size(); i++) {
                    for (int j = 0; j < sent.size(); j++) {
                        LongArrayList feats = new LongArrayList();
                        FastDepParseFe.addArcFactoredMSTFeats(isent, i, j, feats);
                        FeatureVector fv = new FeatureVector();
                        long[] lfeats = feats.getInternalElements();
                        for (int k=0; k<feats.size(); k++) {
                            int hash = (int) ((lfeats[k] ^ (lfeats[k] >>> 32)) & 0xffffffffl);
                            //int hash = MurmurHash.hash32(lfeats[k]); 
                            fv.add(hash, 1.0);
                        }
                    }
                }
                timer.stop();
                n += sent.size();
                if (s % 100 == 0) {
                    log.info("s="+s+" n=" + n + " Toks / sec: " + (n / timer.totSec())); 
                }
                timer.start();
            }
        }
        timer.stop();
        
        log.info("Average ms per sent: " + (timer.totMs() / sents.size() / trials));
        log.info("Toks / sec: " + (sents.getNumTokens() * trials / timer.totSec())); 
        log.info("Alphabet.size(): " + alphabet.size());
    }
    
    public static void main(String[] args) throws ParseException, IOException {
        (new FastDepParseFeSpeedTest()).testSpeed();
    }
    
}
