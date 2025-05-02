package io.github.david0x03;

import java.util.Arrays;

public class FeatureUtils {

    private final static String[] authenticationFeatures = new String[]{
            "Credentials",
            "OneTimePassword",
            "CertificateAuthentication",
            "MultiFactorAuthentication",
            "SingleSignOn",
            "SAML"
    };

    private final static String[] authorizationFeatures = new String[]{
            "AccessQuotaLimitation",
            "AttributeBasedAccessControl",
            "DiscretionaryAccessControl",
            "MandatoryAccessControl",
            "LatticeBasedAccessControl",
            "LocationBasedAccessControl",
            "RoleBasedAccessControl",
            "RuleBasedAccessControl",
            "TimedAccessControl",
            "StateBasedAccessControl",
            "ApplicationModeBasedAccessControl",
            "OAuth2"
    };

    private final static String[] encryptionFeatures = new String[]{
            "StreamCiphers",
            "BlockCiphers",
            "SymmetricKeyCryptography",
            "AsymmetricKeyCryptography",
            "HybridCryptoSystems",
            "DES",
            "3DES",
            "EllipticCurves",
            "DiffieHellman",
            "AES"
    };

    private final static String[] cryptoGraphicHashingFeatures = new String[]{
            "SHA",
            "SHA256",
            "SHA384",
            "SHA512"
    };

    private final static String[] keyManagementFeatures = new String[]{
            "KeyGeneration",
            "KeyDistribution",
            "GroupKeyManagement",
            "KeyStorage",
            "KeyRevocation"
    };

    private final static String[] signatureFeatures = new String[]{
            "MessageSigning",
            "Certification",
            "MessageAuthentication",
            "DigitalWatermarking"
    };

    private final static String[] loggingFeatures = new String[]{"Logging"};

    private final static String[] secureDataHandlingFeatures = new String[]{
            "DataValidation",
            "InputValidation",
            "Blacklisting",
            "Whitelisting",
            "DownloadVerification",
            "OutputValidation",
            "DataSanitization",
            "InputSanitization",
            "OutputSanitization",
            "ParameterizedPreparedStatement",
            "RetentionControl",
            "SecureStorage",
            "TrustedSources",
            "TimeSource",
    };

    private final static String[] sourceOfRandomnessFeatures = new String[]{"SourceOfRandomness"};

    private final static String[] sessionManagementFeatures = new String[]{
            "ReplayAttackPrevention",
            "SessionFixationProtection",
            "SessionTakeoverPrevention",
            "SessionTimeout"
    };

    public static String getSecurityFeatureMainCategory(String feature) {
        if (Arrays.asList(authenticationFeatures).contains(feature)) return "Authentication";
        if (Arrays.asList(authorizationFeatures).contains(feature)) return "Authorization";
        if (Arrays.asList(encryptionFeatures).contains(feature)) return "Encryption";
        if (Arrays.asList(cryptoGraphicHashingFeatures).contains(feature)) return "CryptographicHashing";
        if (Arrays.asList(keyManagementFeatures).contains(feature)) return "KeyManagement";
        if (Arrays.asList(signatureFeatures).contains(feature)) return "Signature";
        if (Arrays.asList(loggingFeatures).contains(feature)) return "Logging";
        if (Arrays.asList(secureDataHandlingFeatures).contains(feature)) return "SecureDataHandling";
        if (Arrays.asList(sourceOfRandomnessFeatures).contains(feature)) return "SourceOfRandomness";
        if (Arrays.asList(sessionManagementFeatures).contains(feature)) return "SessionManagement";

        return feature;
    }

}
