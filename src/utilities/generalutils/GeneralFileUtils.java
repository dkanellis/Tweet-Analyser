/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package utilities.generalutils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 *
 * @author Kanellis Dimitris
 */
public class GeneralFileUtils {

    // FIXME complete stopword file checking
    public static boolean stopwordsCorrupted(File file) throws IOException,
            NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (InputStream input = new FileInputStream(file)) {
            DigestInputStream dinput = new DigestInputStream(input, md);
        }
        byte[] digest = md.digest();

        return true;
    }
}