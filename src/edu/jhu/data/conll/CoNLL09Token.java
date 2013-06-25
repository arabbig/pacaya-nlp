package edu.jhu.data.conll;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

/**
 * One token from a CoNNL-2009 formatted file.
 * 
 * From http://ufal.mff.cuni.cz/conll2009-st/task-description.html
 * 
 * (For the related CoNLL-2008 format description, see
 * http://barcelona.research.yahoo.net/dokuwiki/doku.php?id=conll2008:format)
 * 
 * Columns: ID FORM LEMMA PLEMMA POS PPOS FEAT PFEAT HEAD PHEAD DEPREL PDEPREL
 * FILLPRED PRED APREDs
 * 
 * The P-columns (PLEMMA, PPOS, PFEAT, PHEAD and PDEPREL) are the autoamtically
 * predicted variants of the gold-standard LEMMA, POS, FEAT, HEAD and DEPREL
 * columns. They are produced by independently (or cross-)trained taggers and
 * parsers.
 * 
 * PRED is the same as in the 2008 English data. APREDs correspond to 2008's
 * ARGs. FILLPRED contains Y for lines where PRED is/should be filled.
 * 
 * @author mgormley
 * 
 */
public class CoNLL09Token {
    private static final Pattern whitespace = Pattern.compile("\\s+");
    private static final Pattern verticalBar = Pattern.compile("\\|");
    
    // Field number:    Field name:     Description:
    /** 1    ID  Token counter, starting at 1 for each new sentence. */
    private int id;
    /** 2    FORM    Word form or punctuation symbol. */
    private String form;
    /** 3    LEMMA   Gold lemma or stem (depending on particular data set) of word form, or an underscore if not available. */
    private String lemma;
    /** 3    LEMMA   Predicted lemma or stem (depending on particular data set) of word form, or an underscore if not available. */
    private String plemma;
    /** 5    POS  Gold part-of-speech tag. */
    private String pos;
    /** 5    PPOS  Predicted part-of-speech tag. */
    private String ppos;
    /** 6    FEAT   Gold unordered set of syntactic and/or morphological features (depending on the particular language), separated by a vertical bar (|), or an underscore if not available. */
    private List<String> feat; 
    /** 6    PFEAT   Predicted unordered set of syntactic and/or morphological features (depending on the particular language), separated by a vertical bar (|), or an underscore if not available. */
    private List<String> pfeat; 
    /** 7    HEAD    Gold head of the current token, which is either a value of ID or zero ('0'). Note that depending on the original treebank annotation, there may be multiple tokens with an ID of zero. */
    private int head;
    /** 7    PHEAD    Predicted head of the current token, which is either a value of ID or zero ('0'). Note that depending on the original treebank annotation, there may be multiple tokens with an ID of zero. */
    private int phead;
    /** 8    DEPREL  Gold dependency relation to the HEAD. The set of dependency relations depends on the particular language. Note that depending on the original treebank annotation, the dependency relation may be meaningfull or simply 'ROOT'. */
    private String deprel;
    /** 10   PDEPREL     Predicted dependency relation to the PHEAD, or an underscore if not available. The set of dependency relations depends on the particular language. Note that depending on the original treebank annotation, the dependency relation may be meaningfull or simply 'ROOT'. */
    private String pdeprel;
    /** 10   FILLPRED    Contains Y for lines where PRED is/should be filled. */
    private boolean fillpred;
    /** 10   PRED     Rolesets of the semantic predicates in this sentence. */
    private String pred;
    /** 10   APREDs     Columns with argument labels for the each semantic predicate following textual order, i.e., the first column corresponds to the first predicate in PRED, the second column to the second predicate, etc.*/
    private List<String> apreds;
    
    public CoNLL09Token(String line) {
        // Optional items can take on the dummy value "_".
        // Required: ID, FORM, CPOSTAG, POSTAG, HEAD, DEPREL.
        // Optional: LEMMA, FEATS, PHEAD, PDEPREL.
        
        // Columns: ID FORM LEMMA PLEMMA POS PPOS FEAT PFEAT HEAD PHEAD DEPREL PDEPREL
        // FILLPRED PRED APREDs
        String[] splits = whitespace.split(line);
        id = Integer.parseInt(splits[0]);
        form = splits[1];
        lemma = fromUnderscoreString(splits[2]);
        plemma = fromUnderscoreString(splits[3]);
        pos = fromUnderscoreString(splits[4]);
        ppos = fromUnderscoreString(splits[5]);
        feat = getFeats(fromUnderscoreString(splits[6]));
        pfeat = getFeats(fromUnderscoreString(splits[7]));
        head = Integer.parseInt(splits[8]);
        phead = Integer.parseInt(splits[9]);
        deprel = fromUnderscoreString(splits[10]);
        pdeprel = fromUnderscoreString(splits[11]);
        fillpred = (fromUnderscoreString(splits[12]) != null);
        pred = fromUnderscoreString(splits[13]);
        apreds = new ArrayList<String>(Arrays.asList(Arrays.copyOfRange(splits, 14, splits.length)));
    }
    
    public CoNLL09Token(int id, String form, String lemma, String plemma,
            String pos, String ppos, List<String> feat, List<String> pfeat,
            int head, int phead, String deprel, String pdeprel,
            boolean fillpred, String pred, List<String> apreds) {
        super();
        this.id = id;
        this.form = form;
        this.lemma = lemma;
        this.plemma = plemma;
        this.pos = pos;
        this.ppos = ppos;
        this.feat = feat;
        this.pfeat = pfeat;
        this.head = head;
        this.phead = phead;
        this.deprel = deprel;
        this.pdeprel = pdeprel;
        this.fillpred = fillpred;
        this.pred = pred;
        this.apreds = apreds;
    }

    /** Deep copy constructor */
    public CoNLL09Token(CoNLL09Token other) {
        this.id = other.id;
        this.form = other.form;
        this.lemma = other.lemma;
        this.plemma = other.plemma;
        this.pos = other.pos;
        this.ppos = other.ppos;
        this.feat = other.feat == null ? null : new ArrayList<String>(other.feat);
        this.pfeat = other.pfeat == null ? null : new ArrayList<String>(other.pfeat);
        this.head = other.head;
        this.phead = other.phead;
        this.deprel = other.deprel;
        this.pdeprel = other.pdeprel;
        this.fillpred = other.fillpred;
        this.pred = other.pred;
        this.apreds = other.apreds == null ? null : new ArrayList<String>(other.apreds);
    }

    public void intern() {
        form = form.intern();
        lemma = lemma.intern();
        plemma = plemma.intern();
        pos = pos.intern();
        ppos = ppos.intern();
        feat = CoNLLXToken.getInternedList(feat);
        pfeat = CoNLLXToken.getInternedList(pfeat);
        deprel = deprel.intern();
        pdeprel = pdeprel.intern();
        pred = pred.intern();
        apreds = CoNLLXToken.getInternedList(apreds);
    }

    /**
     * Convert from the underscore representation of an optional string to the null representation.
     */
    private static String fromUnderscoreString(String value) {
        if (value.equals("_")) {
            return null;
        } else {
            return value;
        }
    }


    /**
     * Convert from the null representation of an optional string to the underscore representation.
     */
    private static String toUnderscoreString(String value) {
        if (value == null) {
            return "_";
        } else {
            return value;
        }
    }

    private static List<String> getFeats(String featsStr) {
        if (featsStr == null) {
            return null;
        }
        String[] splits = verticalBar.split(featsStr);
        return Arrays.asList(splits);
    }

    private static String getFeatsString(List<String> feats) {
        if (feats == null) {
            return "_";
        } else if (feats.size() == 0) {
            return "_";
        } else {
            return StringUtils.join(feats, "|");
        }
    }

    @Override
    public String toString() {
        return "CoNLL09Token [id=" + id + ", form=" + form + ", lemma=" + lemma
                + ", plemma=" + plemma + ", pos=" + pos + ", ppos=" + ppos
                + ", feat=" + feat + ", pfeat=" + pfeat + ", head=" + head
                + ", phead=" + phead + ", deprel=" + deprel + ", pdeprel="
                + pdeprel + ", fillpred=" + fillpred + ", pred=" + pred
                + ", apreds=" + apreds + "]";
    }

    public void write(Writer writer) throws IOException {
        final String sep = "\t";

        // Columns: ID FORM LEMMA PLEMMA POS PPOS FEAT PFEAT HEAD PHEAD DEPREL PDEPREL
        // FILLPRED PRED APREDs
        writer.write(String.format("%d", id));
        writer.write(sep);
        writer.write(String.format("%s", form));
        writer.write(sep);
        writer.write(String.format("%s", toUnderscoreString(lemma)));
        writer.write(sep);
        writer.write(String.format("%s", toUnderscoreString(plemma)));
        writer.write(sep);
        writer.write(String.format("%s", toUnderscoreString(pos)));
        writer.write(sep);
        writer.write(String.format("%s", toUnderscoreString(ppos)));
        writer.write(sep);
        writer.write(String.format("%s", getFeatsString(feat)));
        writer.write(sep);
        writer.write(String.format("%s", getFeatsString(pfeat)));
        writer.write(sep);
        writer.write(String.format("%s", Integer.toString(head)));
        writer.write(sep);
        writer.write(String.format("%s", Integer.toString(phead)));
        writer.write(sep);
        writer.write(String.format("%s", toUnderscoreString(deprel)));
        writer.write(sep);
        writer.write(String.format("%s", toUnderscoreString(pdeprel)));
        writer.write(sep);
        writer.write(String.format("%s", fillpred ? "Y" : "_"));
        writer.write(sep);
        writer.write(String.format("%s", toUnderscoreString(pred)));
        writer.write(sep);
        for (int i=0; i<apreds.size(); i++) {
            String apred = apreds.get(i);
            writer.write(String.format("%s", toUnderscoreString(apred)));
            if (i < apreds.size() - 1) {
                writer.write(sep);
            }
        }
    }

    public void writeWithSpaces(Writer writer) throws IOException {
        final String sep = " ";

        // Columns: ID FORM LEMMA PLEMMA POS PPOS FEAT PFEAT HEAD PHEAD DEPREL PDEPREL
        // FILLPRED PRED APREDs
        writer.write(String.format("%-7d", id));
        writer.write(sep);
        writer.write(String.format("%-17s", form));
        writer.write(sep);
        writer.write(String.format("%-17s", toUnderscoreString(lemma)));
        writer.write(sep);
        writer.write(String.format("%-17s", toUnderscoreString(plemma)));
        writer.write(sep);
        writer.write(String.format("%-5s", toUnderscoreString(pos)));
        writer.write(sep);
        writer.write(String.format("%-5s", toUnderscoreString(ppos)));
        writer.write(sep);
        writer.write(String.format("%-32s", getFeatsString(feat)));
        writer.write(sep);
        writer.write(String.format("%-32s", getFeatsString(pfeat)));
        writer.write(sep);
        writer.write(String.format("%-3s", Integer.toString(head)));
        writer.write(sep);
        writer.write(String.format("%-3s", Integer.toString(phead)));
        writer.write(sep);
        writer.write(String.format("%-7s", toUnderscoreString(deprel)));
        writer.write(sep);
        writer.write(String.format("%-7s", toUnderscoreString(pdeprel)));
        writer.write(sep);
        writer.write(String.format("%-7s", fillpred ? "Y" : "_"));
        writer.write(sep);
        writer.write(String.format("%-7s", toUnderscoreString(pred)));
        writer.write(sep);
        for (int i=0; i<apreds.size(); i++) {
            String apred = apreds.get(i);
            writer.write(String.format("%-7s", toUnderscoreString(apred)));
            if (i < apreds.size() - 1) {
                writer.write(sep);
            }
        }
    }

    public int getId() {
        return id;
    }

    public String getForm() {
        return form;
    }

    public String getLemma() {
        return lemma;
    }

    public String getPlemma() {
        return plemma;
    }

    public String getPos() {
        return pos;
    }

    public String getPpos() {
        return ppos;
    }

    public List<String> getFeat() {
        return feat;
    }

    public List<String> getPfeat() {
        return pfeat;
    }

    public int getHead() {
        return head;
    }

    public int getPhead() {
        return phead;
    }

    public String getDeprel() {
        return deprel;
    }

    public String getPdeprel() {
        return pdeprel;
    }

    public boolean isFillpred() {
        return fillpred;
    }

    public String getPred() {
        return pred;
    }

    public List<String> getApreds() {
        return apreds;
    }
    
    public void setPhead(int phead) {
        this.phead = phead;
    }
    
}