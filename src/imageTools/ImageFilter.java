package imageTools;

import java.io.File;

import javax.swing.filechooser.FileFilter;

public class ImageFilter extends FileFilter {

	@Override
	public boolean accept(File f) {
        if (f.isDirectory()) {
            return true;
        }
 
        String fileName = f.getName().toLowerCase();
        if (fileName != null) {
            if (fileName.contains(".jpeg") ||
                fileName.contains(".jpg") ||
                fileName.contains(".png")) {
                    return true;
            } else {
                return false;
            }
        }
 
        return false;
	}

	@Override
	public String getDescription() {
		return "JPEGs and PNGs";
	}

}
