package Model;

import java.util.HashSet;

public class Document {

    private String docName;
    private int maxTf;
    private int uniqueTerms;
    private int docLength;
    private HashSet<Term> potentialEntities;

    /**
     * Constructor
     *
     * @param docName
     * @param maxTf
     * @param uniqueTerms
     */
    public Document(String docName, int maxTf, int uniqueTerms, int docLength, HashSet<Term> potentialEntities) {
        this.docName = docName;
        this.maxTf = maxTf;
        this.uniqueTerms = uniqueTerms;
        this.docLength = docLength;
        this.potentialEntities = potentialEntities;
    }


    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        if (potentialEntities != null) {
            for (Term term : potentialEntities) {
                stringBuilder.append(term.getTermWord() + "~");
            }
        }
        return docName + "," + maxTf + "," + uniqueTerms + "," + docLength + "™" + stringBuilder.toString();
    }

    /**
     * getter method for document's name
     * @return
     */
    public String getDocName() {
        return docName;
    }

    /**
     * getter method for document's max TF field
     * @return
     */
    public int getMaxTf() {
        return maxTf;
    }

    /**
     * getter method for document's amount of unique terms
     * @return
     */
    public int getUniqueTerms() {
        return uniqueTerms;
    }

    /**
     * getter method for document's length
     */
    public int getDocLength() {
        return docLength;
    }
}