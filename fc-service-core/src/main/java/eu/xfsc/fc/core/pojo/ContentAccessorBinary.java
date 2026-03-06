package eu.xfsc.fc.core.pojo;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;

/**
 * A byte-array implementation of the ContentAccessor interface for binary content.
 * Unlike {@link ContentAccessorDirect}, this preserves raw bytes without assuming
 * UTF-8 text encoding. Use {@link #getContentAsStream()} or {@link #getContentAsBytes()}
 * to access the content; {@link #getContentAsString()} is not supported.
 */
@lombok.AllArgsConstructor
public class ContentAccessorBinary implements ContentAccessor {

    private final byte[] content;

    @Override
    public String getContentAsString() {
        throw new UnsupportedOperationException(
                "ContentAccessorBinary holds raw binary data. Use getContentAsStream() or getContentAsBytes() instead.");
    }

    @Override
    public InputStream getContentAsStream() {
        return new ByteArrayInputStream(content);
    }

    @Override
    public byte[] getContentAsBytes() {
        return content.clone();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ContentAccessorBinary other) {
            return Arrays.equals(content, other.content);
        }
        if (obj instanceof ContentAccessor ca) {
            return Arrays.equals(content, ca.getContentAsBytes());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(content);
    }

}