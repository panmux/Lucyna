package indexer;

import org.apache.tika.exception.TikaException;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

class FilesManager {

    private DocumentExtractor extractor;
    private IndexManager index;

    FilesManager(DocumentExtractor extractor, IndexManager index) {
        this.extractor = extractor;
        this.index = index;
    }

    String performOperation(String operationName, String dirPath) {
        //check if file at dirPath exists, is not indexed etc
        if (operationName.equals("--rm") || operationName.equals("--add")) {
            try {
                dirPath = new File(dirPath).getCanonicalPath();
            } catch (IOException e) {
                System.err.println("File is not accessible, cannot perform operation.");
                index.close();
                System.exit(1);
            }
        }

        switch (operationName) {
            case "--add": {
                addToIndex(dirPath);
                break;
            }
            case "--rm": {
                try {
                    index.deleteAllInDirectory(dirPath);
                    index.deleteByField("indexed_path", dirPath);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            }
            case "--list": {
                try {
                    return String.join("\n", index.getWatchedDirectories());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            }
            case "--purge": {
                index.clean();
                break;
            }
            case "--reindex": {
                String[] dirsToAdd = {};
                try {

                    dirsToAdd = index.getWatchedDirectories();

                } catch (IOException e) {
                    e.printStackTrace();
                }
                index.clean();

                for (String toAdd : dirsToAdd) {
                    addToIndex(toAdd);
                }
                break;
            }
        }

        return "";
    }

    String[] getWatchedList() throws IOException {
        return index.getWatchedDirectories();
    }

    void deleteFileFromIndex(Path file) {
        try {
            index.deleteByField("path", file.toString());
        } catch (IOException e) {
            System.err.println("Cannot delete file from index.");
        }
    }

    private void addToIndex(String dirPath) {
        traverse(Paths.get(dirPath), new AddingVisitor());

        index.addDocument(extractor.generatePathDocument(dirPath), "indexed_path");
    }

    private void traverse(Path path, SimpleFileVisitor<Path> visitor) {
        try {
            if (Files.isDirectory(path))
                Files.walkFileTree(path, visitor);
            else
                singleVisit(path, visitor);

        } catch (IOException e) {
            System.err.println("Couldn't go through file directory.");
        }
    }

    private void singleVisit(Path path, SimpleFileVisitor<Path> visitor) throws IOException {
        visitor.visitFile(path, Files.readAttributes(path, BasicFileAttributes.class));
    }

    private class AddingVisitor extends SimpleFileVisitor<Path> {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            try {
                index.addDocument(extractor.extract(file), "path");
            } catch (TikaException | IOException e) {
                //cannot read content so we ignore this file and continue
                return FileVisitResult.CONTINUE;
            }

            return FileVisitResult.CONTINUE;
        }
    }

    void addFile(Path path) {
        try {
            singleVisit(path, new AddingVisitor());
        } catch (IOException e) {
            System.err.println("Unable to open file");
        }
    }
}