package com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.http;

import java.io.IOException;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

/** Auth fields have exact JSON shapes; never coerce numbers or arrays into credentials. */
public final class AuthInputDeserializers {
    private AuthInputDeserializers() {}
    public static final class Password extends StdDeserializer<char[]> {
        public Password(){super(char[].class);}
        public char[] deserialize(JsonParser parser,DeserializationContext context) throws IOException {
            if(!parser.hasToken(JsonToken.VALUE_STRING) || parser.getTextLength()>256)
                return context.reportInputMismatch(char[].class,"Invalid credential field");
            return parser.getText().toCharArray();
        }
    }
    public static final class Username extends StdDeserializer<String> {
        public Username(){super(String.class);}
        public String deserialize(JsonParser parser,DeserializationContext context) throws IOException {
            if(!parser.hasToken(JsonToken.VALUE_STRING) || parser.getTextLength()>128)
                return context.reportInputMismatch(String.class,"Invalid credential field");
            return parser.getText();
        }
    }
}
