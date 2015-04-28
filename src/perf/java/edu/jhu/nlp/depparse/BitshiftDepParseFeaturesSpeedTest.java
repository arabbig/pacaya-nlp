package edu.jhu.nlp.depparse;

import java.io.IOException;

import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.jhu.nlp.data.simple.AlphabetStore;
import edu.jhu.nlp.data.simple.AnnoSentence;
import edu.jhu.nlp.data.simple.AnnoSentenceCollection;
import edu.jhu.nlp.data.simple.AnnoSentenceReaderSpeedTest;
import edu.jhu.nlp.data.simple.IntAnnoSentence;
import edu.jhu.nlp.depparse.BitshiftDepParseFeatureExtractor.BitshiftDepParseFeatureExtractorPrm;
import edu.jhu.nlp.tag.StrictPosTagAnnotator;
import edu.jhu.nlp.words.PrefixAnnotator;
import edu.jhu.pacaya.gm.feat.FeatureVector;
import edu.jhu.pacaya.util.FeatureNames;
import edu.jhu.prim.util.Timer;

public class BitshiftDepParseFeaturesSpeedTest {
    
    private static final Logger log = LoggerFactory.getLogger(BitshiftDepParseFeaturesSpeedTest.class);
    
    /**
     * Speed test results.
     * 
     * Gilim SSD:
     *    w/o                     s=2400 n=169795 Toks / sec: 14677.99
     *    w/Murmur                s=2400 n=169795 Toks / sec: 7094.89
     *    w/BitShift              s=2400 n=169795 Toks / sec: 7590.63
     *    w/IntArrayList+BitShift s=2400 n=169795 Toks / sec: 9111.61
     *    w/DirectToFV+Murmur     s=2400 n=169795 Toks / sec: 10152.16
     *    
     *    On round 2:
     *    w/Carerras          s=800 n=76244 Toks / sec: 1373.54
     *    w/more 2nd-order    s=800 n=76244 Toks / sec: 1013.89
     *    w/Car.DtoFV+Mur     s=800 n=76244 Toks / sec: 1797.48
     *    
     * New Tests:
     *    TurboWordPair (no coarse) s=2200 n=51830 Toks / sec: 5523.231031543051
     *                              (s=2400 n=169795 Toks / sec: 6453.875099775742)
     *    TurboWordPair             s=2400 n=56427 Toks / sec: 4066.5177284520037
     *    ArcFactoredMST            s=2400 n=56427 Toks / sec: 5755.507955936352
     *                              (s=2400 n=169795 Toks / sec: 6781.221294780143)
     *    ArcFactoredMST (w/coarse) s=2400 n=56427 Toks / sec: 3116.480724621672
     *    
     *    TurboWordPair (tfs)       s=2400 n=169795 Toks / sec: 3735.781390948494
     *    
     *    w/MST & Carerras          s=800 n=19560 Toks / sec: 928.6426434980773
     *    w/Turbo & Carerras        s=800 n=19560 Toks / sec: 1008.0395794681509
     *    w/Turbo                   s=400 n=9669 Toks / sec: 123.39361145497008
     *    w/Turbo (Max 10 heads)    s=800 n=19560 Toks / sec: 1077.2704741972793
     */
    //@Test
    public void testSpeed() throws ParseException, IOException {
        AnnoSentenceCollection sents = AnnoSentenceReaderSpeedTest.readPtbYmConllx();
        PrefixAnnotator.addPrefixes(sents);
        StrictPosTagAnnotator.addStrictPosTags(sents);
        AlphabetStore store = new AlphabetStore(sents);
        
        int trials = 3;
        
        FeatureNames alphabet = new FeatureNames();
        
        BitshiftDepParseFeatureExtractorPrm prm = new BitshiftDepParseFeatureExtractorPrm();
        prm.useMstFeats = false;
        prm.useCarerrasFeats = false;
        prm.useMorphologicalFeats = false;
        
        Timer timer = new Timer();
        timer.start();
        int n=0;
        for (int trial = 0; trial < trials; trial++) {
            for (int s=0; s<sents.size(); s++) {
                AnnoSentence sent = sents.get(s);
                IntAnnoSentence isent = new IntAnnoSentence(sent, store);
                for (int i = -1; i < sent.size(); i++) {
                    for (int j = 0; j < sent.size(); j++) {
                        FeatureVector feats = new FeatureVector();
                        BitshiftDepParseFeatures.addArcFeats(isent, i, j, prm, feats);

                        if (true) {
                            for (int k=0; k<sent.size(); k++) {
                                if (k == i || k == j) { continue; }
                                boolean isNonprojectiveGrandparent = (i < j && k < i) || (j < i && i < k);
                                if (!isNonprojectiveGrandparent) {
                                    BitshiftDepParseFeatures.addGrandparentFeats(isent, k, i, j, feats, prm);
                                }
                                if (j < k) {
                                    BitshiftDepParseFeatures.addArbitrarySiblingFeats(isent, i, j, k, feats, prm);
                                }
                            }
                        }
                        
//                        FeatureVector fv = new FeatureVector();
//                        long[] lfeats = feats.getInternalElements();
//                        for (int k=0; k<feats.size(); k++) {
//                            int hash = (int) ((lfeats[k] ^ (lfeats[k] >>> 32)) & 0xffffffffl);
//                            //int hash = MurmurHash.hash32(lfeats[k]); 
//                            fv.add(hash, 1.0);
//                        }
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
        (new BitshiftDepParseFeaturesSpeedTest()).testSpeed();
    }
    
}
