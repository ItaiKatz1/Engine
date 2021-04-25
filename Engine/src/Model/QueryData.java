package Model;

public class QueryData {
    private String queryID;
    private String docID;
    private int numOfRelevantDocs;

    /**
     * Constructor
     * @param queryID
     * @param docID
     * @param numOfRelevantDocs
     */
    public QueryData(String queryID,String docID, int numOfRelevantDocs){
        this.queryID=queryID;
        this.docID=docID;
        this.numOfRelevantDocs=numOfRelevantDocs;
    }

    /**
     * getter method for the query's ID
     * @return queryId
     */
    public String getQueryID() {
        return queryID;
    }

    /**
     * getter method for the Doc's ID
     * @return docID
     */
    public String getDocID() {
        return docID;
    }

    /**
     * setter method for the query's ID
     * @param queryID
     */
    public void setQueryID(String queryID) {
        this.queryID = queryID;
    }

    /**
     * setter method for the doc's ID
     * @param docID
     */
    public void setDocID(String docID) {
        this.docID = docID;
    }

    /**
     * getter method for the number of relevant documents
     * @return
     */
    public int getNumOfRelevantDocs() {
        return numOfRelevantDocs;
    }

    /**
     * setter method for the number of relevant documents
     * @param numOfRelevantDocs
     */
    public void setNumOfRelevantDocs(int numOfRelevantDocs) {
        this.numOfRelevantDocs = numOfRelevantDocs;
    }
}