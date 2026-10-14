package io.aigoodle.knowledge.index;

/** Encodes vectors for storage in a portable relational text column. */
final class JdbcVectorCodec {

    private JdbcVectorCodec() {}

    static String encode(float[] vector) {
        StringBuilder encoded = new StringBuilder();
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) {
                encoded.append(',');
            }
            encoded.append(vector[index]);
        }
        return encoded.toString();
    }

    static float[] decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return new float[0];
        }
        String[] components = encoded.split(",");
        float[] vector = new float[components.length];
        for (int index = 0; index < components.length; index++) {
            vector[index] = Float.parseFloat(components[index]);
        }
        return vector;
    }
}
