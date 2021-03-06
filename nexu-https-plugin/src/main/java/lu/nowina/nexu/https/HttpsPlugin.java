/**
 * © Nowina Solutions, 2015-2015
 *
 * Concédée sous licence EUPL, version 1.1 ou – dès leur approbation par la Commission européenne - versions ultérieures de l’EUPL (la «Licence»).
 * Vous ne pouvez utiliser la présente œuvre que conformément à la Licence.
 * Vous pouvez obtenir une copie de la Licence à l’adresse suivante:
 *
 * http://ec.europa.eu/idabc/eupl5
 *
 * Sauf obligation légale ou contractuelle écrite, le logiciel distribué sous la Licence est distribué «en l’état»,
 * SANS GARANTIES OU CONDITIONS QUELLES QU’ELLES SOIENT, expresses ou implicites.
 * Consultez la Licence pour les autorisations et les restrictions linguistiques spécifiques relevant de la Licence.
 */
package lu.nowina.nexu.https;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lu.nowina.nexu.NexuException;
import lu.nowina.nexu.api.EnvironmentInfo;
import lu.nowina.nexu.api.NexuAPI;
import lu.nowina.nexu.api.plugin.InitializationMessage;
import lu.nowina.nexu.api.plugin.InitializationMessage.MessageType;
import lu.nowina.nexu.api.plugin.NexuPlugin;
import net.lingala.zip4j.core.ZipFile;

/**
 * NexU plugin that will perform all initialization tasks required by NexU to perform HTTPS.
 * 
 * @author Jean Lepropre (jean.lepropre@nowina.lu)
 */
public class HttpsPlugin implements NexuPlugin {

	private static final Logger LOGGER = LoggerFactory.getLogger(HttpsPlugin.class.getName());
	
	public HttpsPlugin() {
		super();
	}

	@Override
	public List<InitializationMessage> init(String pluginId, NexuAPI api) {
		final ResourceBundle resourceBundle = ResourceBundle.getBundle("bundles/https");
		final ResourceBundle baseResourceBundle = ResourceBundle.getBundle("bundles/nexu");
		
		LOGGER.info("Verify if keystore is ready");
		File nexuHome = api.getAppConfig().getNexuHome();
		File keyStoreFile = new File(nexuHome, "keystore.jks");
		final File caCert;
		if (!keyStoreFile.exists()) {
			caCert = createKeystore(nexuHome);
		} else {
			caCert = getKeystore(nexuHome);
		}
		return installCaCert(api, caCert, resourceBundle, baseResourceBundle);
	}

	/**
	 * Create a keystore in the directory given in parameters
	 * 
	 * @param nexuHome
	 * 
	 * @return A file containing the CA cert of the keystore
	 */
	File createKeystore(File nexuHome) {

		try {
			File keyStoreFile = new File(nexuHome, "keystore.jks");
			LOGGER.info("Creating keystore " + keyStoreFile.getAbsolutePath());

			PKIManager pki = new PKIManager();
			KeyPair pair = pki.createKeyPair();

			Calendar cal = Calendar.getInstance();
			Date notBefore = cal.getTime();
			cal.add(Calendar.YEAR, 10);
			Date notAfter = cal.getTime();

			X509Certificate cert = pki.generateSelfSignedCertificate(pair.getPrivate(), pair.getPublic(), notBefore, notAfter, "cn=localhost, O=Nexu, C=LU");

			KeyStore keyStore = KeyStore.getInstance("JKS");
			keyStore.load(null, null);
			FileOutputStream output = new FileOutputStream(keyStoreFile);
			keyStore.setKeyEntry("localhost", pair.getPrivate(), "password".toCharArray(), new Certificate[] { cert });
			keyStore.store(output, "password".toCharArray());
			output.close();

			File caCert = new File(nexuHome, "ca-cert.crt");
			FileOutputStream caOutput = new FileOutputStream(caCert);
			caOutput.write(cert.getEncoded());
			caOutput.close();

			return caCert;
		} catch (Exception e) {
			throw new RuntimeException("Cannot create keystore", e);
		}

	}

	private File getKeystore(File nexuHome) {
		return new File(nexuHome, "ca-cert.crt");
	}
	
	private List<InitializationMessage> installCaCert(final NexuAPI api, final File caCert, final ResourceBundle resourceBundle, final ResourceBundle baseResourceBundle) {
		final List<InitializationMessage> messages = new ArrayList<>();
		final EnvironmentInfo envInfo = EnvironmentInfo.buildFromSystemProperties(System.getProperties());
		switch(envInfo.getOs()) {
		case WINDOWS:
			messages.addAll(installCaCertInFirefoxForWindows(api, caCert, resourceBundle, baseResourceBundle));
			messages.addAll(installCaCertInWindowsStore(api, caCert, resourceBundle, baseResourceBundle));
			break;
		default:
			LOGGER.warn("Automatic installation of CA certficate is not yet supported for " + envInfo.getOs());
		}
		return messages;
	}
	
	/**
	 * Install the CA Cert in Firefox for Windows
	 * 
	 * @param caCert
	 */
	private List<InitializationMessage> installCaCertInFirefoxForWindows(final NexuAPI api, final File caCert, final ResourceBundle resourceBundle, final ResourceBundle baseResourceBundle) {
		Path tempDirPath = null;
		try {
			// 1. Copy and unzip firefox_add-certs-nowina-1.1.zip
			tempDirPath = Files.createTempDirectory("NexU-Firefox-Add_certs");
			final File tempDirFile = tempDirPath.toFile();
			final File zipFile = new File(tempDirFile, "firefox_add-certs-nowina-1.1.zip");
			FileUtils.copyURLToFile(this.getClass().getResource("/firefox_add-certs-nowina-1.1.zip"), zipFile);
			new ZipFile(zipFile).extractAll(tempDirPath.toString());
			
			// 2. Install caCert into <unzipped_folder>/cacert
			final File unzippedFolder = new File(tempDirFile.getAbsolutePath() + File.separator +
					"firefox_add-certs-nowina-1.1");
			final File caCertDestDir = new File(unzippedFolder, "cacert");
			FileUtils.copyFile(caCert, new File(caCertDestDir, caCert.getName()));
			
			// 3. Run add-certs.cmd
			final Process p = Runtime.getRuntime().exec(unzippedFolder + File.separator + "add-certs.cmd");
			if(!p.waitFor(180, TimeUnit.SECONDS)) {
				throw new NexuException("Timeout occurred when trying to install CA certificate in Firefox");
			}
			if(p.exitValue() != 0) {
				throw new NexuException("Batch script returned " + p.exitValue() + " when trying to install CA certificate in Firefox");
			}
			return Collections.emptyList();
		} catch(Exception e) {
			LOGGER.warn("Exception when trying to install certificate in Firefox", e);
			return Arrays.asList(new InitializationMessage(
					MessageType.CONFIRMATION,
					resourceBundle.getString("warn.install.cert.title"),
					MessageFormat.format(resourceBundle.getString("warn.install.cert.header"), api.getAppConfig().getApplicationName(), "FireFox"),
					baseResourceBundle.getString("provide.feedback"),
					true,
					e
				)
			);
		} finally {
			if(tempDirPath != null) {
				try {
					FileUtils.deleteDirectory(tempDirPath.toFile());
				} catch (IOException e) {
					LOGGER.error("IOException when deleting " + tempDirPath.toString() + ": " + e.getMessage(), e);
				}
			}
		}
	}

	/**
	 * Installs the CA certificate in Windows Store (used by Chrome, IE and Edge amongst others).
	 * @param caCert The certificate to install.
	 */
	private List<InitializationMessage> installCaCertInWindowsStore(final NexuAPI api, final File caCert, final ResourceBundle resourceBundle, final ResourceBundle baseResourceBundle) {
		try (
				final FileInputStream fis = new FileInputStream(caCert);
				final BufferedInputStream bis = new BufferedInputStream(fis);
				) {
			final KeyStore keyStore = KeyStore.getInstance("Windows-ROOT");
			keyStore.load(null);

			final CertificateFactory cf = CertificateFactory.getInstance("X.509");
			final Certificate cert = cf.generateCertificate(bis);

			if(keyStore.getCertificateAlias(cert) == null) {
				keyStore.setCertificateEntry(api.getAppConfig().getApplicationName() + "-localhost", cert); 
			}
			return Collections.emptyList();
		} catch(final KeyStoreException e) {
			LOGGER.warn("KeyStoreException when trying to install certificate in Windows Store", e);
			// Unfortunately there is no particular exception thrown in this case
			return Arrays.asList(new InitializationMessage(
					MessageType.CONFIRMATION,
					resourceBundle.getString("warn.install.cert.title"),
					MessageFormat.format(resourceBundle.getString("warn.install.cert.header"), api.getAppConfig().getApplicationName(), "Windows Store"),
					resourceBundle.getString("warn.install.cert.windows.registry") + "\n\n" + baseResourceBundle.getString("provide.feedback"),
					true,
					e
				)
			);
		} catch(final Exception e) {
			LOGGER.warn("Exception when trying to install certificate in Windows Store", e);
			return Arrays.asList(new InitializationMessage(
					MessageType.CONFIRMATION,
					resourceBundle.getString("warn.install.cert.title"),
					MessageFormat.format(resourceBundle.getString("warn.install.cert.header"), api.getAppConfig().getApplicationName(), "Windows Store"),
					baseResourceBundle.getString("provide.feedback"),
					true,
					e
				)
			);
		}
	}
}
