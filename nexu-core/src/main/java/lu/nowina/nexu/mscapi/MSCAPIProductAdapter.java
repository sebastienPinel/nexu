/**
 * © Nowina Solutions, 2015-2016
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
package lu.nowina.nexu.mscapi;

import java.util.ArrayList;
import java.util.List;

import eu.europa.esig.dss.DSSException;
import eu.europa.esig.dss.DigestAlgorithm;
import eu.europa.esig.dss.SignatureValue;
import eu.europa.esig.dss.ToBeSigned;
import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import eu.europa.esig.dss.token.MSCAPISignatureToken;
import eu.europa.esig.dss.token.PasswordInputCallback;
import eu.europa.esig.dss.token.SignatureTokenConnection;

import lu.nowina.nexu.api.CertificateFilter;
import lu.nowina.nexu.api.ConfiguredKeystore;
import lu.nowina.nexu.api.GetIdentityInfoResponse;
import lu.nowina.nexu.api.MSCAPIKeyStore;
import lu.nowina.nexu.api.NexuAPI;
import lu.nowina.nexu.api.Product;
import lu.nowina.nexu.api.ProductAdapter;
import lu.nowina.nexu.api.SystrayMenuItem;
import lu.nowina.nexu.api.flow.FutureOperationInvocation;
import lu.nowina.nexu.api.flow.NoOpFutureOperationInvocation;

/**
 * Product adapter for {@link ConfiguredKeystore}.
 *
 * @author Jean Lepropre (jean.lepropre@nowina.lu)
 */
public class MSCAPIProductAdapter implements ProductAdapter {

	public MSCAPIProductAdapter() {
		super();
	}

	@Override
	public boolean accept(Product product) {
		return product instanceof MSCAPIKeyStore;
	}

	@Override
	public SignatureTokenConnection connect(NexuAPI api, Product product, PasswordInputCallback callback) {
		final MSCAPIKeyStore configuredKeystore = (MSCAPIKeyStore) product;
		return new MSCAPITokenProxy(configuredKeystore, callback);
	}

	@Override
	public boolean canReturnIdentityInfo(Product product) {
		return false;
	}

	@Override
	public GetIdentityInfoResponse getIdentityInfo(SignatureTokenConnection token) {
		throw new IllegalStateException("This product adapter cannot return identity information.");
	}

	@Override
	public boolean supportCertificateFilter(Product product) {
		return false;
	}

	@Override
	public List<DSSPrivateKeyEntry> getKeys(SignatureTokenConnection token, CertificateFilter certificateFilter) {
		throw new IllegalStateException("This product adapter does not support certificate filter.");
	}

	@Override
	public boolean canReturnSuportedDigestAlgorithms(Product product) {
		return false;
	}

	@Override
	public List<DigestAlgorithm> getSupportedDigestAlgorithms(Product product) {
		throw new IllegalStateException("This product adapter cannot return list of supported digest algorithms.");
	}

	@Override
	public DigestAlgorithm getPreferredDigestAlgorithm(Product product) {
		throw new IllegalStateException("This product adapter cannot return list of supported digest algorithms.");
	}

	@Override
	@SuppressWarnings("unchecked")
	public FutureOperationInvocation<Product> getConfigurationOperation(NexuAPI api, Product product) {
		return new NoOpFutureOperationInvocation<Product>(product);
	}

	@Override
	@SuppressWarnings("unchecked")
	public FutureOperationInvocation<Boolean> getSaveOperation(NexuAPI api, Product product) {
		return new NoOpFutureOperationInvocation<Boolean>(true);
	}

	@Override
	public SystrayMenuItem getExtensionSystrayMenuItem() {
		return null;
	}

	@Override
	public List<Product> detectProducts() {
		final List<Product> products = new ArrayList<>();
		products.add(new MSCAPIKeyStore());
		return products;
	}

	private static class MSCAPITokenProxy implements SignatureTokenConnection {

		private SignatureTokenConnection proxied;

		private final MSCAPIKeyStore configuredKeystore;

		private final PasswordInputCallback callback;

		public MSCAPITokenProxy(MSCAPIKeyStore configuredKeystore, PasswordInputCallback callback) {
			super();
			this.configuredKeystore = configuredKeystore;
			this.callback = callback;
		}

		private void initSignatureTokenConnection() {
			if (proxied != null) {
				return;
			}
			proxied = new MSCAPISignatureToken();
		}

		@Override
		public void close() {
			final SignatureTokenConnection stc = proxied;
			// Always nullify proxied even in case of exception when calling
			// close()
			proxied = null;
			stc.close();
		}

		@Override
		public List<DSSPrivateKeyEntry> getKeys() throws DSSException {
			initSignatureTokenConnection();
			return proxied.getKeys();
		}

		@Override
		public SignatureValue sign(ToBeSigned toBeSigned, DigestAlgorithm digestAlgorithm, DSSPrivateKeyEntry keyEntry)
				throws DSSException {
			initSignatureTokenConnection();
			return proxied.sign(toBeSigned, digestAlgorithm, keyEntry);
		}
	}
}
