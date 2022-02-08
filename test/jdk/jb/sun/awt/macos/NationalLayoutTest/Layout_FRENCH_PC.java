/*
 * Copyright 2000-2019 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * @test
 * @summary Regression test for IDEA-165950: National keyboard layouts support
 * @requires (jdk.version.major >= 8 & os.family == "mac")
 * @modules java.desktop/sun.lwawt.macosx
 * @run main NationalLayoutTest FRENCH_PC
 */

/*
 * Enumerates keys under test for com.apple.keylayout.French-PC (macOS 10.14.5)
 */
public enum Layout_FRENCH_PC implements LayoutKey {

    // Enum name must be the same as KeyEvent.VK_ constant name corresponding to the key on US keyboard layout
    // Note that '\u0000' may be used if no char is mapped to a key + modifier or if one wants to skip its testing

    VK_MINUS         (')', ']', '°', ']', '\u001B'),
    VK_EQUALS        ('=', '}', '+', '≠', '\u001F'),

    VK_OPEN_BRACKET  (KeyChar.dead('^'), KeyChar.ch('ô'), KeyChar.dead('¨'), KeyChar.ch('Ô'), KeyChar.ch('\u001E')),
    VK_CLOSE_BRACKET ('$', '¤', '£', '¥', '\u001D'),

    VK_SEMICOLON     ('m', 'µ', 'M', 'Ó', '\r'),
    VK_QUOTE         ('ù', 'Ù', '%', '‰', 'ù'),
    VK_BACK_SLASH    ('*', '@', 'μ', '#', '\u001C'),

    VK_BACK_QUOTE    ('<', '«', '>', '≥', '<'),
    VK_COMMA         (';', '…', '.', '•', ';'),
    VK_PERIOD        (':', '÷', '/', '\\', ':'),
    VK_SLASH         ('!', '¡', '§', '±', '='),

    ;

    // Common code for any LayoutKey enum

    private final Key key;

    Layout_FRENCH_PC(char no, char alt, char shift, char alt_shift, char control) {
        key = new Key(name(), new MappedKeyChars(no, alt, shift, alt_shift, control));
    }

    Layout_FRENCH_PC(KeyChar no, KeyChar alt, KeyChar shift, KeyChar alt_shift, KeyChar control) {
        key = new Key(name(), new MappedKeyChars(no, alt, shift, alt_shift, control));
    }

    public Key getKey() {
        return key;
    }
}
