package org.eljhoset.persistencepg.persistence;

record Path(String from, String to) {
    static Path empty() {
        return new Path("", "");
    }
}
