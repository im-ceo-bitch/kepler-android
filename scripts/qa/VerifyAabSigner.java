import java.io.OutputStream;
import java.nio.file.Path;
import java.security.CodeSigner;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.jar.JarFile;

/** Verify every payload entry, not just the presence of a JAR signature block. */
class VerifyAabSigner {
    private static boolean signatureMetadata(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        if (!upper.startsWith("META-INF/")) return false;
        String file = upper.substring("META-INF/".length());
        return !file.contains("/") && (file.equals("MANIFEST.MF") || file.endsWith(".SF")
                || file.endsWith(".RSA") || file.endsWith(".DSA") || file.endsWith(".EC") || file.startsWith("SIG-"));
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 2 || !args[1].matches("[0-9A-Fa-f]{64}")) {
            throw new IllegalArgumentException("Usage: VerifyAabSigner.java <aab> <upload certificate SHA-256>");
        }
        String expected = args[1].toUpperCase(Locale.ROOT);
        int checked = 0;
        var names = new HashSet<String>();
        try (JarFile jar = new JarFile(Path.of(args[0]).toFile(), true)) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                String name = entry.getName();
                if (!names.add(name)) throw new SecurityException("Duplicate archive entry: " + name);
                if (entry.isDirectory() || signatureMetadata(name)) continue;
                try (var input = jar.getInputStream(entry)) {
                    input.transferTo(OutputStream.nullOutputStream());
                }
                CodeSigner[] signers = entry.getCodeSigners();
                if (signers == null || signers.length != 1) throw new SecurityException("Missing or ambiguous signer: " + name);
                var cert = signers[0].getSignerCertPath().getCertificates().get(0);
                String fingerprint = HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()));
                if (!fingerprint.equals(expected)) throw new SecurityException("Wrong upload certificate: " + name);
                checked++;
            }
        }
        if (checked == 0) throw new SecurityException("Archive has no signed payload");
        System.out.println("AAB signed payload entries verified: " + checked + "; upload certificate: " + expected);
    }
}
