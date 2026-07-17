enum UiLanguage {
    FRENCH, CHINESE;

    String text(String french, String chinese) {
        return this == FRENCH ? french : chinese;
    }

    UiLanguage next() {
        return this == FRENCH ? CHINESE : FRENCH;
    }
}
