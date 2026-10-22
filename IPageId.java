/**
 * Interface representing a unique identifier for a disk storage page
 * in the database management system.
 * <p>
 * Implementations must uniquely identify a physical or logical page on disk
 * and properly implement value-based equality.
 */
public class PageId implements IPageId {

    private int Idpage; //pour retrouver le numero/identifiant de page

    public PageId(int Idpage) {
        this.Idpage = Idpage;
    }

    public int getIdpage() {
        return Idpage;
    }

    @Override
    //pour comparer deux numéros de page
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        //verifie si obj n'est pas null et est une page
        if (obj == null || !(obj instanceof PageId)) {
            return false;
        }

        PageId autre = (PageId) obj;
        return this.Idpage == autre.Idpage;
    }

    @Override
    public String toString() {
        return "l'identifant de la page : " + Idpage + "\n";
    }
}
