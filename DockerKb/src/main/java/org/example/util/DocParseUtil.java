package org.example.util;
/**
 * @project backend
 *
 * @author qy
 * @date 2026/10/14 13:05
 */

import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * @description
 * @author qy
 * @Date 2026/10/14  13:05
 */
@Component
public class DocParseUtil {
    private final Tika tika = new Tika();
    public String parseFile(MultipartFile file) throws Exception{
        return tika.parseToString(file.getInputStream());
    }
}
