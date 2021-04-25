package Model;

import com.medallia.word2vec.Searcher;
import com.medallia.word2vec.Word2VecModel;
import javafx.util.Pair;

import java.io.*;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.*;

public class Ranker {

    private Indexer rankerIndexer;
    private HashMap<Term, LinkedList<String>> queryTermsAndPostings;
    private HashMap<Document, HashMap<Term, String[]>> relevantDocsAndData;
    private HashMap<String, Document> allDocs;
    private String path;
    private boolean isSemantic;
    private boolean isStemming;

    private double averageDocLength;

    private HashMap<String, Double> documentsBM25Score;
    private HashMap<String, Double> documentsPersonalizedScore;
    private HashMap<String, String> potentialEntities;
    private DecimalFormat decimalFormat;

    private HashSet<String> originalQueryWords;

    /**
     * Constructor
     *
     * @param indexer
     * @param path
     * @param allDocs
     */
    public Ranker(Indexer indexer, String path, HashMap<String, Document> allDocs, boolean isSemantic, boolean isStemming) {

        rankerIndexer = indexer;
        this.path = path;
        queryTermsAndPostings = new HashMap<>();
        relevantDocsAndData = new HashMap<>();
        documentsBM25Score = new HashMap<>();
        documentsPersonalizedScore = new HashMap<>();
        this.allDocs = allDocs;
        originalQueryWords = new HashSet<>();
        calculateAverageDocumentLength(allDocs);
        decimalFormat = new DecimalFormat("#.###");
        decimalFormat.setRoundingMode(RoundingMode.DOWN);
        this.isSemantic = isSemantic;
        this.isStemming = isStemming;

    }

    /**
     * Constructor
     *
     * @param indexer
     * @param path
     * @param isStemming
     */
    public Ranker(Indexer indexer, String path, boolean isStemming) {
        rankerIndexer = indexer;
        this.path = path;
        decimalFormat = new DecimalFormat("#.###");
        decimalFormat.setRoundingMode(RoundingMode.DOWN);
        this.isStemming = isStemming;
    }


    /**
     * The method receives the query's words after parsing and returns the relevant document's ID's and their rankings
     *
     * @param parsedQuery
     * @return
     * @throws IOException
     * @throws Searcher.UnknownWordException
     */
    public HashMap<String, Double> findRelevantDocuments(ArrayList<Term> parsedQuery) throws IOException, Searcher.UnknownWordException {
        return rankRelevantDocuments(parsedQuery);
    }


    /**
     * The method receives the query's words after parsing and returns the relevant document's ID's and their ranking
     *
     * @param parsedQuery
     * @return
     */
    private HashMap<String, Double> rankRelevantDocuments(ArrayList<Term> parsedQuery) throws IOException {

        //first, by going over each word of the query, find the matching line in the posting files if exists
        if (!isSemantic) {
            for (Term currTerm : parsedQuery) {
                LinkedList<String> temp = rankerIndexer.findTermInDictionary(currTerm.getTermWord());
                if (temp != null) {
                    queryTermsAndPostings.put(currTerm, temp);
                } else {
                    queryTermsAndPostings.put(currTerm, new LinkedList<>());
                }
            }
        } else {
            for (Term term : parsedQuery) {
                originalQueryWords.add(term.getTermWord());
            }
            ArrayList<Term> semanticQuery = getSemanticQuery(parsedQuery);
            for (Term currTerm : semanticQuery) {

                LinkedList<String> temp = rankerIndexer.findTermInDictionary(currTerm.getTermWord());
                if (temp == null) {
                    queryTermsAndPostings.put(currTerm, new LinkedList<>());
                } else {
                    queryTermsAndPostings.put(currTerm, temp);
                }
            }
        }
        //we create a data-structure for each document to hold the query words which are relevant for it
        relevantDocsAndData = buildRelevantDocsStructure();

        //from here we rank the documents using the BM-25 score and our personal way of ranking them into two different data structures
        getBM25Score();
        getTermScore();

        //from here we create a new score for each document, based on all ranks we did before hand
        HashMap<String, Double> finalRanksForEachDocument = new HashMap<>();
        double finalScore;

        for (HashMap.Entry<String, Double> entry : documentsBM25Score.entrySet()) {
            double bm25CurrentScore = entry.getValue();
            double personalizedScore = documentsPersonalizedScore.get(entry.getKey());
            if(!isSemantic) {
                finalScore = bm25CurrentScore * 0.65 + personalizedScore * 0.35;
            } else {
                finalScore = bm25CurrentScore * 0.8 + personalizedScore * 0.2;
            }
            finalRanksForEachDocument.put(entry.getKey(), finalScore);
        }

        queryTermsAndPostings.clear();
        relevantDocsAndData.clear();
        documentsBM25Score.clear();
        documentsPersonalizedScore.clear();
        if (isSemantic) {
            originalQueryWords.clear();
        }
        return finalRanksForEachDocument;
    }

    /**
     * The method receives all the documents from the secondary memory and calculates the average length of a document
     *
     * @param allDocs
     */
    private void calculateAverageDocumentLength(HashMap<String, Document> allDocs) {

        double numOfDocs = allDocs.size();
        double lengthOfAllDocs = 0;

        for (Document length : allDocs.values()) {
            lengthOfAllDocs = lengthOfAllDocs + length.getDocLength();
        }

        averageDocLength = lengthOfAllDocs / numOfDocs;
    }


    /**
     * The method calculates IDF and returns the value for the calling method
     *
     * @param
     * @return log of tempCalculation
     */
    private double calculateIDF(double amountOfDocsTermAppearedIn) {

        double amountOfAllDocs = relevantDocsAndData.size();
        double tempCalculation = amountOfAllDocs / amountOfDocsTermAppearedIn;
        return Math.log10(tempCalculation);
    }


    /**
     * The method calculates the BM25 score for each document
     */
    private void getBM25Score() {

        double k1 = 2; //can also be 2.0
        double b = 0.55;
        double currIDFScore;
        double termFrequencyInDoc;
        double docLength;
        double amountOfDocumentsTermAppearedIn;
        double BM25CurrentScore;
        String[] termDataFromPosting;


        for (Document currDoc : relevantDocsAndData.keySet()) {

            BM25CurrentScore = 0;
            HashMap<Term, String[]> currDocData = relevantDocsAndData.get(currDoc);
            docLength = currDoc.getDocLength();

            for (HashMap.Entry<Term, String[]> entry : currDocData.entrySet()) {

                termDataFromPosting = entry.getValue();
                termFrequencyInDoc = Integer.parseInt(termDataFromPosting[1]);
                amountOfDocumentsTermAppearedIn = queryTermsAndPostings.get(entry.getKey()).size();
                currIDFScore = calculateIDF(amountOfDocumentsTermAppearedIn);
                double tempBM25 = currIDFScore * termFrequencyInDoc * (k1 + 1) / (termFrequencyInDoc + k1 * (1 - b) + b * docLength / averageDocLength);
                if (!isSemantic) {
                    BM25CurrentScore = BM25CurrentScore + tempBM25;
                } else {
                    if(originalQueryWords.contains(entry.getKey().getTermWord())){
                        BM25CurrentScore = BM25CurrentScore + tempBM25;
                    } else {
                        BM25CurrentScore = BM25CurrentScore + tempBM25 * 0.25;
                    }
                }
            }

            documentsBM25Score.put(currDoc.getDocName(), BM25CurrentScore);
        }
    }


    /**
     * The method calculates an additional score for each document based on their words appearing in the document's title and on their relative location in the document itself.
     */
    private void getTermScore() {

        double score;

        for (Document currDoc : relevantDocsAndData.keySet()) {

            score = 0;
            String[] termDataFromPosting;
            HashMap<Term, String[]> currDocData = relevantDocsAndData.get(currDoc);

            for (HashMap.Entry<Term, String[]> entry : currDocData.entrySet()) {

                termDataFromPosting = entry.getValue();
                int isHeader = Integer.parseInt(termDataFromPosting[2]);
                int relativeLocation = Integer.parseInt(termDataFromPosting[4]);
                if (relativeLocation < 26) {
                    score = score + 1;
                }

                if (isHeader == 1) {
                    score = score + 1;
                }
            }

            documentsPersonalizedScore.put(currDoc.getDocName(), score);
        }
    }


    /**
     * this function gets the doc name and returns a list of the most ranked entities in this article
     *
     * @param docName the name of the doc
     * @return rankedEntities a list of the entities
     * @throws IOException
     */
    public LinkedList<String> findMostRankedEntities(String docName) throws IOException {
        LinkedList<String> rankedEntities = new LinkedList<>();
        if (potentialEntities == null) {
            readPotentialEntitiesFromDisk();
        }
        String infoOnDoc = potentialEntities.get(docName);
        rankedEntities = removeWrongEntities(infoOnDoc);

        //here we need to send the list to calculation in the ranker
        rankedEntities = getSortedEntitiesList(rankedEntities, docName);

        return rankedEntities;
    }


    /**
     * this function build a data structure of docname->and his potential entities then it returns the string of the doc we are looking for
     *
     * @throws IOException
     */
    private void readPotentialEntitiesFromDisk() throws IOException {
        potentialEntities = new HashMap<>();
        File file = new File(path + "/documents/documents.txt");
        if (file.exists()) {
            //BufferedReader reader = new BufferedReader(new FileReader(file));
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
            String line;
            StringBuilder stringBuilder = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
                String[] docInfo = stringBuilder.toString().split(",");
                potentialEntities.put(docInfo[0], line);
                stringBuilder = new StringBuilder();
            }
            reader.close();
        }
    }


    /**
     * this function get the doc name, takes his entities from the disk and then for each entity check if he is in the dictionary
     *
     * @param infoOnDoc the doc name
     * @return the linked list with the real entities
     * @throws IOException
     */
    private LinkedList<String> removeWrongEntities(String infoOnDoc) throws IOException {
        LinkedList<String> realEntities = new LinkedList<>();
        HashSet<String> entitiesWithNoDuplicates = new HashSet<>();
        HashMap<String, Pair<Integer, Integer>> mainDictionary = rankerIndexer.getMainDictionary();
        String[] firstSplit = infoOnDoc.split("™"); //here we are separating the doc info from the potential entities
        String[] secSplit = firstSplit[1].split("~");
        for (String termInfo : secSplit) {
            String[] termWord = termInfo.split(",");
            if (mainDictionary.containsKey(termWord[0])) {
                entitiesWithNoDuplicates.add(termInfo);
                //realEntities.add(termInfo);
            }
        }
        for (String term : entitiesWithNoDuplicates) {
            realEntities.add(term);
        }
        return realEntities;
    }

    /**
     * this function return the 5 most ranked entities in the list sorted from the most ranked to the less one
     *
     * @param allEntitiesInDoc unsorted linkedlist that may contain more than 5 entities
     * @return a sorted list with 5 elements or less
     * @throws IOException
     */
    private LinkedList<String> getSortedEntitiesList(LinkedList<String> allEntitiesInDoc, String docName) throws
            IOException {
        LinkedList<String> rankedEntities = new LinkedList<>();
        Model.Searcher searcher = new Model.Searcher(path, isStemming);
        //HashMap<String, Integer> termsTf = rankerIndexer.readTermsTfFromDisk();
        PriorityQueue<Pair> pQueue = new PriorityQueue<>(new Comparator<Pair>() {
            @Override
            public int compare(Pair o1, Pair o2) {
                if ((Double) o1.getValue() - (Double) o2.getValue() < 0) {
                    return 1;
                } else if ((Double) o1.getValue() - (Double) o2.getValue() > 0) {
                    return -1;
                } else {
                    return 0;
                }
            }
        });
        if (allEntitiesInDoc != null) {
            if (allEntitiesInDoc.size() > 0) {
                for (String term : allEntitiesInDoc) {
                    LinkedList<String> infoOnEntity = rankerIndexer.findTermInDictionary(term);
                    double idf = calculateIDF(infoOnEntity);
                    double tf = calculateTF(infoOnEntity, docName);
                    double rank = idf * tf;
                    Pair<String, Double> pair = new Pair<>(term, rank);
                    pQueue.add(pair);
                }
                int limit = 5;
                while (pQueue.size() > 0 && limit > 0) {
                    Pair tempPair = pQueue.poll();
                    rankedEntities.add(tempPair.getKey() + " : " + decimalFormat.format(tempPair.getValue()));
                    limit--;
                }
            }
        }
        return rankedEntities;
    }


    /**
     * The method receives an array-list of terms representing the original query and returns a list of terms representing the semantic matches to the original query
     *
     * @param parsedQuery
     * @return
     * @throws IOException
     * @throws Searcher.UnknownWordException
     */
    private ArrayList<Term> getSemanticQuery(ArrayList<Term> parsedQuery) throws IOException {

        ArrayList<Term> semanticQuery = new ArrayList<>();
        Word2VecModel model = Word2VecModel.fromTextFile(new File("./Resources/word2vec.c.output.model.txt"));
        com.medallia.word2vec.Searcher semanticSearcher = model.forSearch();
        int numOfResultsInList = 3;


        for (Term currTerm : parsedQuery) {
            try {
                List<com.medallia.word2vec.Searcher.Match> matches = semanticSearcher.getMatches(currTerm.getTermWord(), numOfResultsInList);

                for (Searcher.Match match : matches) {
                    semanticQuery.add(new Term(match.match(), currTerm.getDoc()));
                }
            } catch (com.medallia.word2vec.Searcher.UnknownWordException e) {
                //TERM NOT KNOWN TO MODEL
            }
        }

        return semanticQuery;
    }


    /**
     * this function calculates the TF value for an entity
     *
     * @param infoOnDoc the info from the posting file
     * @param docName   the name of the doc
     * @return the value of the TF
     */
    private double calculateTF(LinkedList<String> infoOnDoc, String docName) {
        double sum = 0;
        if (infoOnDoc == null) {
            return sum;
        }
        if (infoOnDoc.size() == 0) {
            return sum;
        }
        for (String doc : infoOnDoc) {
            String[] firstSplit = doc.split(",");
            if (firstSplit[0].equals(docName)) {
                double appearInDoc = Double.parseDouble(firstSplit[1]);
                double length = allDocs.get(docName).getDocLength();
                sum = appearInDoc / length;
                return sum;
            }
        }
        return sum;
    }

    /**
     * this function calculates the IDF value of each entity in the corpus
     *
     * @param infoOnDoc the info on the entity from the posting file
     * @return sum
     */
    private double calculateIDF(LinkedList<String> infoOnDoc) {
        double sum = 0;
        if (infoOnDoc == null) {
            return sum;
        }
        if (infoOnDoc.size() == 0) {
            return sum;
        } else {
            double appearInCorpus = infoOnDoc.size();
            double numOfDocs = allDocs.size();
            sum = Math.log10(numOfDocs / appearInCorpus);
        }
        return sum;
    }


    /**
     * The method receives indication if we're working on the semantic structure or the regular, then builds a new data structure
     * where each document holds which terms of the query are relevant for him
     *
     * @param
     * @return docsRankingStructure
     */
    private HashMap<Document, HashMap<Term, String[]>> buildRelevantDocsStructure() {

        HashMap<Document, HashMap<Term, String[]>> docsRankingStructure = new HashMap<>();

        for (HashMap.Entry<Term, LinkedList<String>> entry : queryTermsAndPostings.entrySet()) {
            LinkedList<String> currTerm = entry.getValue();

            for (String docData : currTerm) {
                String[] docDataSplit = docData.split(",");
                Document tempDoc = allDocs.get(docDataSplit[0]);

                if (!docsRankingStructure.containsKey(tempDoc)) {
                    HashMap<Term, String[]> temp = new HashMap<>();
                    temp.put(entry.getKey(), docDataSplit);
                    docsRankingStructure.put(tempDoc, temp);

                } else {
                    if (!docsRankingStructure.get(tempDoc).containsKey(entry.getKey())) { //to avoid an issue we created during parsing
                        docsRankingStructure.get(tempDoc).put(entry.getKey(), docDataSplit);
                    } else {
                        int updatedTf = Integer.parseInt(docDataSplit[1]);
                        String[] postingToUpdate = docsRankingStructure.get(tempDoc).get(entry.getKey());
                        updatedTf = Integer.parseInt(postingToUpdate[1]) + updatedTf;
                        docsRankingStructure.get(tempDoc).remove(entry.getKey());
                        postingToUpdate[1] = Integer.toString(updatedTf);
                        docsRankingStructure.get(tempDoc).put(entry.getKey(), postingToUpdate);
                    }
                }
            }
        }


        return docsRankingStructure;
    }
}