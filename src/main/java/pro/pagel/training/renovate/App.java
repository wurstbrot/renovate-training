package pro.pagel.training.renovate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Minimale Demo-Anwendung für das Renovate-Training.
 *
 * <p>Die Anwendung schreibt das übergebene Argument ins Log. Mit der in der pom.xml
 * eingetragenen Log4j-Version 2.14.1 ist genau dieses Muster (Benutzereingabe landet im Log)
 * der Angriffsweg von Log4Shell (CVE-2021-44228). Im Training wird die Lücke nicht ausgenutzt,
 * sondern durch Renovate geschlossen.
 */
public final class App {

    private static final Logger LOG = LogManager.getLogger(App.class);

    private App() {
    }

    public static void main(String[] args) {
        String name = args.length > 0 ? String.join(" ", args) : "Renovate";
        LOG.info("Hallo {}! Java {} auf {}", name,
                System.getProperty("java.version"), System.getProperty("os.name"));
    }
}
