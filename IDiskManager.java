
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;

public class DiskManager implements IDiskManager {

    private String dmDir;
    private int pageTaille; //pageSize
    private int nbPage;
    private ArrayList<Integer> pageLibre;
    private RandomAccessFile fichierPage;
    private boolean initAppelee;

    public DiskManager() {
        dmDir = null;
        pageTaille = 0;
        nbPage = 0;
        pageLibre = new ArrayList<Integer>();
        fichierPage = null;
        initAppelee = false;
    }

    public void Init(String dmDir, int pageSize)
            throws DiskManagerException {

        // Init doit être appelée qu'une seule fois
        if (initAppelee) {
            throw new DiskManagerException(
                "Init a déjà été appelée."
            );
        }

        initAppelee = true;

        // vérification chemin pas fournie ou chaine vide 
        if (dmDir == null || dmDir.isEmpty() || pageSize <= 0) {
            throw new DiskManagerException(
                "Paramètres invalides."
            );
        }

        File dossier = new File(dmDir); //fcréation chemin

        try {

            if (!dossier.exists() && !dossier.mkdirs()) {
                throw new DiskManagerException(
                    "Impossible de créer le dossier."
                );
            }

            if (!dossier.isDirectory()) {
                throw new DiskManagerException(
                    "Le chemin indiqué n'est pas un dossier."
                );
            }

            // Vérifie si le dossier est vide, pouvoir utiliser sa longueur
            String[] contenu = dossier.list();

            if (contenu == null) {
                throw new DiskManagerException(
                    "Impossible de lire le dossier."
                );
            }

            File fichier = new File(dossier, "pages.data");
            File metadata = new File(dossier, "metadata.data");

            if (contenu.length == 0) {
                // Initialiser un nouveau gestionnaire
                this.dmDir = dossier.getAbsolutePath();
                this.pageTaille = pageSize;
                this.nbPage = 0;
                this.pageLibre.clear();

                fichierPage = new RandomAccessFile(fichier, "rw");
                Save();
            } else {
                // Un dossier existant doit contenir les deux fichiers
                if (!fichier.exists() || !metadata.exists()) {
                    throw new DiskManagerException(
                        "Les fichiers du gestionnaire sont incomplets."
                    );
                }

                // Lire les métadonnées existantes
                try (DataInputStream in = new DataInputStream(
                        Files.newInputStream(metadata.toPath()))) {

                    int version = in.readInt();
                    int tailleStockee = in.readInt();
                    int nombreStocke = in.readInt();
                    int nombreLibre = in.readInt();

                    if (version != 1
                            || tailleStockee != pageSize
                            || nombreStocke < 0
                            || nombreLibre < 0
                            || nombreLibre > nombreStocke) {
                        throw new DiskManagerException(
                            "Métadonnées invalides ou incompatibles."
                        );
                    }

                    ArrayList<Integer> libres = new ArrayList<Integer>();

                    for (int i = 0; i < nombreLibre; i++) {
                        int numero = in.readInt();

                        if (numero < 0 || numero >= nombreStocke
                                || libres.contains(numero)) {
                            throw new DiskManagerException(
                                "Liste des pages libres invalide."
                            );
                        }

                        libres.add(numero);
                    }

                    if (in.read() != -1) {
                        throw new DiskManagerException(
                            "Métadonnées invalides."
                        );
                    }

                    if (fichier.length()
                            != (long) nombreStocke * tailleStockee) {
                        throw new DiskManagerException(
                            "Taille du fichier de pages incorrecte."
                        );
                    }

                    this.dmDir = dossier.getAbsolutePath();
                    this.pageTaille = tailleStockee;
                    this.nbPage = nombreStocke;
                    this.pageLibre = libres;
                }

                fichierPage = new RandomAccessFile(fichier, "rw");
            }

        } catch (IOException e) {
            fermerFichier();

            dmDir = null;
            pageTaille = 0;
            nbPage = 0;
            pageLibre.clear();

            throw new DiskManagerException(
                "Erreur d'initialisation : " + e.getMessage()
            );
        } catch (DiskManagerException e) {
            fermerFichier();

            dmDir = null;
            pageTaille = 0;
            nbPage = 0;
            pageLibre.clear();

            throw e;
        }
    }

    public void Save() throws DiskManagerException {

        verifierInitialisation();

        File metadata = new File(dmDir, "metadata.data");

        try (DataOutputStream out = new DataOutputStream(
                Files.newOutputStream(metadata.toPath()))) {

            // Sauvegarder la version, la taille et le nombre de pages
            out.writeInt(1);
            out.writeInt(pageTaille);
            out.writeInt(nbPage);
            out.writeInt(pageLibre.size());

            // Sauvegarder les numéros des pages libres
            for (int numero : pageLibre) {
                out.writeInt(numero);
            }

        } catch (IOException e) {
            throw new DiskManagerException(
                "Erreur de sauvegarde : " + e.getMessage()
            );
        }
    }

    public IPageId AllocPage() throws DiskManagerException {

        verifierInitialisation();

        int numero;

        if (!pageLibre.isEmpty()) {
            // Réutiliser une page libérée
            numero = pageLibre.remove(0);
        } else {
            // Créer une nouvelle page à la fin du fichier
            numero = nbPage;

            try {
                fichierPage.setLength(
                    (long) (nbPage + 1) * pageTaille
                );
                nbPage++;
            } catch (IOException e) {
                throw new DiskManagerException(
                    "Erreur d'allocation : " + e.getMessage()
                );
            }
        }

        return new PageId(numero);
    }

    public void ReadPage(IPageId ipid, ByteBuffer buffer)
            throws DiskManagerException {

        int numero = verifierPage(ipid);
        verifierBuffer(buffer);

        byte[] donnees = new byte[pageTaille];

        try {
            fichierPage.seek((long) numero * pageTaille);
            fichierPage.readFully(donnees);

            // Copier les données dans le buffer
            buffer.clear();
            buffer.put(donnees);
            buffer.flip();

        } catch (IOException e) {
            throw new DiskManagerException(
                "Erreur de lecture : " + e.getMessage()
            );
        }
    }

    public void WritePage(IPageId ipid, ByteBuffer buffer)
            throws DiskManagerException {

        int numero = verifierPage(ipid);
        verifierBuffer(buffer);

        byte[] donnees = new byte[pageTaille];

        // Copier une page complète sans modifier la position du buffer original
        ByteBuffer copie = buffer.duplicate();
        copie.clear();
        copie.get(donnees);

        try {
            fichierPage.seek((long) numero * pageTaille);
            fichierPage.write(donnees);

        } catch (IOException e) {
            throw new DiskManagerException(
                "Erreur d'écriture : " + e.getMessage()
            );
        }
    }

    public void DeallocPage(IPageId ipid)
            throws DiskManagerException {

        int numero = verifierPage(ipid);

        // Une page libérée pourra être réutilisée
        pageLibre.add(numero);
    }

    @Override
    public int GetActivePageCount() {
        return nbPage - pageLibre.size();
    }

    @Override
    public int GetPageSize() {
        return pageTaille;
    }

    // Vérifie que le gestionnaire est initialisé
    private void verifierInitialisation()
            throws DiskManagerException {

        if (dmDir == null || fichierPage == null) {
            throw new DiskManagerException(
                "Le gestionnaire n'est pas initialisé."
            );
        }
    }

    // Vérifie qu'un identifiant désigne une page active
    private int verifierPage(IPageId ipid)
            throws DiskManagerException {

        verifierInitialisation();

        if (!(ipid instanceof PageId)) {
            throw new DiskManagerException(
                "Identifiant de page invalide."
            );
        }

        int numero = ((PageId) ipid).getPageIdx();

        if (numero < 0 || numero >= nbPage
                || pageLibre.contains(numero)) {
            throw new DiskManagerException(
                "Page inexistante ou déjà libérée."
            );
        }

        return numero;
    }

    // Vérifier que le buffer peut contenir une page entière
    private void verifierBuffer(ByteBuffer buffer)
            throws DiskManagerException {

        if (buffer == null || buffer.capacity() < pageTaille) {
            throw new DiskManagerException(
                "Le buffer est trop petit."
            );
        }
    }

    // Fermer le fichier en cas d'erreur d'initialisation
    private void fermerFichier() {
        if (fichierPage != null) {
            try {
                fichierPage.close();
            } catch (IOException e) {
                // Rien à faire ici
            }
            fichierPage = null;
        }
    }
}
