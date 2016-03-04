package edu.jhu.nlp.data.simple;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import edu.stanford.nlp.io.StringOutputStream;

public class JsonSentWriterTest {

    public static final String expectedStr = "{\"words\":[\"dog\",\"spied\",\"the\",\"cat\",\"from\",\"MD\"]\n"
            + ",\"prefixes\":[\"prefixes0\",\"prefixes1\",\"prefixes2\",\"prefixes3\",\"prefixes4\",\"prefixes5\"]\n"
            + ",\"lemmas\":[\"lemmas0\",\"lemmas1\",\"lemmas2\",\"lemmas3\",\"lemmas4\",\"lemmas5\"]\n"
            + ",\"posTags\":[\"posTags0\",\"posTags1\",\"posTags2\",\"posTags3\",\"posTags4\",\"posTags5\"]\n"
            + ",\"cposTags\":[\"cposTags0\",\"cposTags1\",\"cposTags2\",\"cposTags3\",\"cposTags4\",\"cposTags5\"]\n"
            + ",\"strictPosTags\":[\"NOUN\",\"OTHER\",\"NOUN\",\"VERB\",\"PUNC\",\"NOUN\"]\n"
            + ",\"clusters\":[\"clusters0\",\"clusters1\",\"clusters2\",\"clusters3\",\"clusters4\",\"clusters5\"]\n"
            + ",\"chunks\":[\"chunks0\",\"chunks1\",\"chunks2\",\"chunks3\",\"chunks4\",\"chunks5\"]\n"
            + ",\"neTags\":[\"neTags0\",\"neTags1\",\"neTags2\",\"neTags3\",\"neTags4\",\"neTags5\"]\n"
            + ",\"parents\":[-1,0,1,2,3]\n"
            + ",\"deprels\":[\"deprels0\",\"deprels1\",\"deprels2\",\"deprels3\",\"deprels4\",\"deprels5\"]\n"
            + ",\"nePairs\":\"[{\\\"m1\\\":{\\\"start\\\":2,\\\"end\\\":6,\\\"head\\\":3,\\\"type\\\":\\\"MAMMAL\\\",\\\"subtype\\\":\\\"CAT\\\",\\\"phraseType\\\":\\\"noun\\\",\\\"id\\\":\\\"uuid2\\\"},\\\"m2\\\":{\\\"start\\\":5,\\\"end\\\":6,\\\"head\\\":5,\\\"type\\\":\\\"LOCATION\\\",\\\"subtype\\\":\\\"STATE\\\",\\\"phraseType\\\":\\\"noun\\\",\\\"id\\\":\\\"uuid3\\\"}},{\\\"m1\\\":{\\\"start\\\":0,\\\"end\\\":1,\\\"head\\\":0,\\\"type\\\":\\\"MAMMAL\\\",\\\"subtype\\\":\\\"DOG\\\",\\\"phraseType\\\":\\\"noun\\\",\\\"id\\\":\\\"uuid1\\\"},\\\"m2\\\":{\\\"start\\\":2,\\\"end\\\":6,\\\"head\\\":3,\\\"type\\\":\\\"MAMMAL\\\",\\\"subtype\\\":\\\"CAT\\\",\\\"phraseType\\\":\\\"noun\\\",\\\"id\\\":\\\"uuid2\\\"}},{\\\"m1\\\":{\\\"start\\\":0,\\\"end\\\":1,\\\"head\\\":0,\\\"type\\\":\\\"MAMMAL\\\",\\\"subtype\\\":\\\"DOG\\\",\\\"phraseType\\\":\\\"noun\\\",\\\"id\\\":\\\"uuid1\\\"},\\\"m2\\\":{\\\"start\\\":0,\\\"end\\\":1,\\\"head\\\":0,\\\"type\\\":\\\"MAMMAL\\\",\\\"subtype\\\":\\\"DOG\\\",\\\"phraseType\\\":\\\"noun\\\",\\\"id\\\":\\\"uuid1\\\"}}]\"\n"
            + ",\"relLabels\":[\"ART-SUBART(Arg-1,Arg-2)\",\"SEE-SUBSEE(Arg-1,Arg-1)\",\"SELF-SUBSELF(Arg-1,Arg-1)\"]}\n"
            + "\n"
            + "{\"words\":[\"dog\",\"spied\",\"the\",\"cat\",\"from\",\"MD\"]\n"
            + ",\"prefixes\":[\"prefixes0\",\"prefixes1\",\"prefixes2\",\"prefixes3\",\"prefixes4\",\"prefixes5\"]\n"
            + ",\"lemmas\":[\"lemmas0\",\"lemmas1\",\"lemmas2\",\"lemmas3\",\"lemmas4\",\"lemmas5\"]\n"
            + ",\"posTags\":[\"posTags0\",\"posTags1\",\"posTags2\",\"posTags3\",\"posTags4\",\"posTags5\"]\n"
            + ",\"cposTags\":[\"cposTags0\",\"cposTags1\",\"cposTags2\",\"cposTags3\",\"cposTags4\",\"cposTags5\"]\n"
            + ",\"strictPosTags\":[\"NOUN\",\"OTHER\",\"NOUN\",\"VERB\",\"PUNC\",\"NOUN\"]\n"
            + ",\"clusters\":[\"clusters0\",\"clusters1\",\"clusters2\",\"clusters3\",\"clusters4\",\"clusters5\"]\n"
            + ",\"chunks\":[\"chunks0\",\"chunks1\",\"chunks2\",\"chunks3\",\"chunks4\",\"chunks5\"]\n"
            + ",\"neTags\":[\"neTags0\",\"neTags1\",\"neTags2\",\"neTags3\",\"neTags4\",\"neTags5\"]\n"
            + ",\"parents\":[-1,0,1,2,3]\n"
            + ",\"deprels\":[\"deprels0\",\"deprels1\",\"deprels2\",\"deprels3\",\"deprels4\",\"deprels5\"]\n"
            + ",\"nePairs\":\"[{\\\"m1\\\":{\\\"start\\\":2,\\\"end\\\":6,\\\"head\\\":3,\\\"type\\\":\\\"MAMMAL\\\",\\\"subtype\\\":\\\"CAT\\\",\\\"phraseType\\\":\\\"noun\\\",\\\"id\\\":\\\"uuid2\\\"},\\\"m2\\\":{\\\"start\\\":5,\\\"end\\\":6,\\\"head\\\":5,\\\"type\\\":\\\"LOCATION\\\",\\\"subtype\\\":\\\"STATE\\\",\\\"phraseType\\\":\\\"noun\\\",\\\"id\\\":\\\"uuid3\\\"}},{\\\"m1\\\":{\\\"start\\\":0,\\\"end\\\":1,\\\"head\\\":0,\\\"type\\\":\\\"MAMMAL\\\",\\\"subtype\\\":\\\"DOG\\\",\\\"phraseType\\\":\\\"noun\\\",\\\"id\\\":\\\"uuid1\\\"},\\\"m2\\\":{\\\"start\\\":2,\\\"end\\\":6,\\\"head\\\":3,\\\"type\\\":\\\"MAMMAL\\\",\\\"subtype\\\":\\\"CAT\\\",\\\"phraseType\\\":\\\"noun\\\",\\\"id\\\":\\\"uuid2\\\"}},{\\\"m1\\\":{\\\"start\\\":0,\\\"end\\\":1,\\\"head\\\":0,\\\"type\\\":\\\"MAMMAL\\\",\\\"subtype\\\":\\\"DOG\\\",\\\"phraseType\\\":\\\"noun\\\",\\\"id\\\":\\\"uuid1\\\"},\\\"m2\\\":{\\\"start\\\":0,\\\"end\\\":1,\\\"head\\\":0,\\\"type\\\":\\\"MAMMAL\\\",\\\"subtype\\\":\\\"DOG\\\",\\\"phraseType\\\":\\\"noun\\\",\\\"id\\\":\\\"uuid1\\\"}}]\"\n"
            + ",\"relLabels\":[\"ART-SUBART(Arg-1,Arg-2)\",\"SEE-SUBSEE(Arg-1,Arg-1)\",\"SELF-SUBSELF(Arg-1,Arg-1)\"]}\n"
            + "\n";


    @Test
    public void testWriteOnly() throws Exception {
        AnnoSentence sent = SimpleTextWriterTest.get6WordAnnoSentence();
        sent.setRelations(null);
        sent.setNamedEntities(null);
        
        StringOutputStream os = new StringOutputStream();
        JsonSentWriter w = new JsonSentWriter(os);
        w.write(sent);
        w.write(sent);
        w.close();
        String str = os.toString();
        
        System.out.println(str);
        assertEquals(expectedStr, str);
    }

}
