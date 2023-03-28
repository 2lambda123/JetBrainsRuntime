package com.jetbrains;

import java.awt.*;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

public interface FontExtensions {
    /**
     * The list of all supported features. For feature description look at
     * https://learn.microsoft.com/en-us/typography/opentype/spec/featurelistThis features will be adding
     * The following features: KERN, LIGA, CALT are missing intentionally. These features will be added automatically via text
     * attributes of Font
     * Adding TextAttribute.KERNING to Font's values manage by KERN feature
     * Adding TextAttribute.LIGATURES to Font's values manage by LIGA and CALT features
     */
    enum FeatureTag {
        AALT, ABVF, ABVM, ABVS, AFRC, AKHN, BLWF, BLWM, BLWS, CASE, CCMP, CFAR, CHWS, CJCT, CLIG, CPCT, CPSP, CSWH, CURS,
        CV01, CV02, CV03, CV04, CV05, CV06, CV07, CV08, CV09, CV10, CV11, CV12, CV13, CV14, CV15, CV16, CV17, CV18, CV19,
        CV20, CV21, CV22, CV23, CV24, CV25, CV26, CV27, CV28, CV29, CV30, CV31, CV32, CV33, CV34, CV35, CV36, CV37, CV38,
        CV39, CV40, CV41, CV42, CV43, CV44, CV45, CV46, CV47, CV48, CV49, CV50, CV51, CV52, CV53, CV54, CV55, CV56, CV57,
        CV58, CV59, CV60, CV61, CV62, CV63, CV64, CV65, CV66, CV67, CV68, CV69, CV70, CV71, CV72, CV73, CV74, CV75, CV76,
        CV77, CV78, CV79, CV80, CV81, CV82, CV83, CV84, CV85, CV86, CV87, CV88, CV89, CV90, CV91, CV92, CV93, CV94, CV95,
        CV96, CV97, CV98, CV99, C2PC, C2SC, DIST, DLIG, DNOM, DTLS, EXPT, FALT, FIN2, FIN3, FINA, FLAC, FRAC, FWID, HALF,
        HALN, HALT, HIST, HKNA, HLIG, HNGL, HOJO, HWID, INIT, ISOL, ITAL, JALT, JP78, JP83, JP90, JP04, LFBD, LJMO, LNUM,
        LOCL, LTRA, LTRM, MARK, MED2, MEDI, MGRK, MKMK, MSET, NALT, NLCK, NUKT, NUMR, ONUM, OPBD, ORDN, ORNM, PALT, PCAP,
        PKNA, PNUM, PREF, PRES, PSTF, PSTS, PWID, QWID, RAND, RCLT, RKRF, RLIG, RPHF, RTBD, RTLA, RTLM, RUBY, RVRN, SALT,
        SINF, SIZE, SMCP, SMPL, SS01, SS02, SS03, SS04, SS05, SS06, SS07, SS08, SS09, SS10, SS11, SS12, SS13, SS14, SS15,
        SS16, SS17, SS18, SS19, SS20, SSTY, STCH, SUBS, SUPS, SWSH, TITL, TJMO, TNAM, TNUM, TRAD, TWID, UNIC, VALT, VATU,
        VCHW, VERT, VHAL, VJMO, VKNA, VKRN, VPAL, VRT2, VRTR, ZERO
    }

    class Feature {
        private final FeatureTag featureTag;
        private final int value;

        public Feature(FeatureTag featureTag) {
            this.featureTag = featureTag;
            this.value = 1;
        }

        public Feature(FeatureTag featureTag, int value) {
            this.featureTag = featureTag;
            this.value = value;
        }

        private String getTagName() {
            return featureTag.toString().toLowerCase();
        }
    }

    class Features {
        private final Set<Feature> features;

        public Features(Set<Feature> features) {
            this.features = features;
        }

        public TreeMap<String, Integer> toMap() {
            TreeMap<String, Integer> res = new TreeMap<>();
            features.forEach((Feature feature) -> res.put(feature.getTagName(), feature.value));
            return res;
        }

        public static Optional<FeatureTag> getFeatureTag(String str) {
            try {
                return Optional.of(FeatureTag.valueOf(str.toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
        }
    }

    Font deriveFontWithFeatures(Font font, Features fontFeatures);

    // use only for testing purpose
    @Deprecated
    String getFeaturesAsString(Font font);

    Dimension getSubpixelResolution();
}