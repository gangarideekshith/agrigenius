package com.agrigenius.chat.assistant;

import java.util.Map;

/**
 * Fixed replies used instead of model text when the assistant has no verified basis to answer
 * ({@code NO_SOURCES}) or the request is out of scope ({@code REFUSED}). Fixed text guarantees that
 * these paths can never carry unsupported advice.
 *
 * <p>English is authoritative. The hi/te/kn/ta texts are DRAFT translations and need review by
 * native speakers before release.
 */
final class FallbackMessages {

    private static final Map<String, String> NO_SOURCES = Map.of(
            "en", "I don't have verified information to answer this. Please contact your local Krishi Vigyan Kendra (KVK) "
                    + "or call the Kisan Call Centre at 1800-180-1551.",
            // DRAFT translation
            "hi", "इस प्रश्न का उत्तर देने के लिए मेरे पास प्रमाणित जानकारी नहीं है। कृपया अपने नज़दीकी कृषि विज्ञान केंद्र (KVK) "
                    + "से संपर्क करें या किसान कॉल सेंटर 1800-180-1551 पर कॉल करें।",
            // DRAFT translation
            "te", "దీనికి సమాధానం ఇవ్వడానికి నా దగ్గర ధృవీకరించిన సమాచారం లేదు. దయచేసి మీ దగ్గరలోని కృషి విజ్ఞాన కేంద్రం (KVK)ని "
                    + "సంప్రదించండి లేదా కిసాన్ కాల్ సెంటర్ 1800-180-1551కి కాల్ చేయండి.",
            // DRAFT translation
            "kn", "ಇದಕ್ಕೆ ಉತ್ತರಿಸಲು ನನ್ನ ಬಳಿ ದೃಢೀಕೃತ ಮಾಹಿತಿ ಇಲ್ಲ. ದಯವಿಟ್ಟು ನಿಮ್ಮ ಹತ್ತಿರದ ಕೃಷಿ ವಿಜ್ಞಾನ ಕೇಂದ್ರ (KVK) ಸಂಪರ್ಕಿಸಿ "
                    + "ಅಥವಾ ಕಿಸಾನ್ ಕಾಲ್ ಸೆಂಟರ್ 1800-180-1551ಗೆ ಕರೆ ಮಾಡಿ.",
            // DRAFT translation
            "ta", "இதற்குப் பதிலளிக்க என்னிடம் சரிபார்க்கப்பட்ட தகவல் இல்லை. உங்கள் அருகிலுள்ள கிருஷி விஞ்ஞான கேந்திரா (KVK)வைத் "
                    + "தொடர்பு கொள்ளவும் அல்லது கிசான் கால் சென்டர் 1800-180-1551 ஐ அழைக்கவும்.");

    private static final Map<String, String> REFUSED = Map.of(
            "en", "I can only help with farming questions using verified information, and I can't give field-specific "
                    + "fertilizer or pesticide doses, prices, weather or scheme eligibility. Please contact your local "
                    + "Krishi Vigyan Kendra (KVK) or agriculture officer.",
            // DRAFT translation
            "hi", "मैं केवल प्रमाणित जानकारी के आधार पर खेती से जुड़े प्रश्नों में मदद कर सकता हूँ। मैं खेत-विशेष खाद या कीटनाशक की "
                    + "मात्रा, भाव, मौसम या योजना की पात्रता नहीं बता सकता। कृपया अपने नज़दीकी कृषि विज्ञान केंद्र (KVK) या कृषि अधिकारी "
                    + "से संपर्क करें।",
            // DRAFT translation
            "te", "నేను ధృవీకరించిన సమాచారంతో వ్యవసాయ ప్రశ్నలకు మాత్రమే సహాయం చేయగలను. పొలానికి నిర్దిష్టమైన ఎరువు లేదా పురుగుమందు "
                    + "మోతాదులు, ధరలు, వాతావరణం లేదా పథకాల అర్హత చెప్పలేను. దయచేసి మీ దగ్గరలోని కృషి విజ్ఞాన కేంద్రం (KVK) లేదా "
                    + "వ్యవసాయ అధికారిని సంప్రదించండి.",
            // DRAFT translation
            "kn", "ನಾನು ದೃಢೀಕೃತ ಮಾಹಿತಿಯೊಂದಿಗೆ ಕೃಷಿ ಪ್ರಶ್ನೆಗಳಿಗೆ ಮಾತ್ರ ಸಹಾಯ ಮಾಡಬಲ್ಲೆ. ಹೊಲಕ್ಕೆ ನಿರ್ದಿಷ್ಟವಾದ ಗೊಬ್ಬರ ಅಥವಾ ಕೀಟನಾಶಕದ "
                    + "ಪ್ರಮಾಣ, ಬೆಲೆಗಳು, ಹವಾಮಾನ ಅಥವಾ ಯೋಜನೆಯ ಅರ್ಹತೆ ಹೇಳಲಾರೆ. ದಯವಿಟ್ಟು ನಿಮ್ಮ ಹತ್ತಿರದ ಕೃಷಿ ವಿಜ್ಞಾನ ಕೇಂದ್ರ (KVK) ಅಥವಾ "
                    + "ಕೃಷಿ ಅಧಿಕಾರಿಯನ್ನು ಸಂಪರ್ಕಿಸಿ.",
            // DRAFT translation
            "ta", "சரிபார்க்கப்பட்ட தகவல்களுடன் விவசாயக் கேள்விகளுக்கு மட்டுமே என்னால் உதவ முடியும். வயலுக்கான குறிப்பிட்ட உரம் அல்லது "
                    + "பூச்சிக்கொல்லி அளவுகள், விலைகள், வானிலை அல்லது திட்டத் தகுதியை என்னால் சொல்ல முடியாது. உங்கள் அருகிலுள்ள "
                    + "கிருஷி விஞ்ஞான கேந்திரா (KVK) அல்லது வேளாண் அலுவலரைத் தொடர்பு கொள்ளவும்.");

    private FallbackMessages() {
    }

    static String noSources(String language) {
        return NO_SOURCES.getOrDefault(language, NO_SOURCES.get("en"));
    }

    static String refused(String language) {
        return REFUSED.getOrDefault(language, REFUSED.get("en"));
    }
}
