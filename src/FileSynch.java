import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class FileSynch {

    public static void main(String[] args) {
//        System.out.println("Hello! Compile app! Example: javac FileSynch.java");
//        System.out.println("Then run app. Example: java FileSynch d:\\tempFrom d:\\tempDest");
        if (args.length != 2) {
            System.out.println("Wrong argument! Example: java FileSynch d:\\tempFrom d:\\tempDest");
            System.exit(0);
        }

        Path sourcePath = Paths.get(args[0]);
        Path destPath = Paths.get(args[1]);
        Map<Path, Long> directoriesAndFilesSource = null;

        if ((Files.exists(sourcePath))) {
            //получаем мапу всех директорий в исходной папке
            directoriesAndFilesSource = directory(sourcePath);
        } else{
            System.out.println("Не верно указан путь к директории source!");
            System.exit(0);
        }

            //если целевая папка не существует - создаем ее
            if (Files.notExists(destPath)) {
                try {
                    Files.createDirectory(destPath);
                } catch (IOException e) {
                    System.out.println("Не удалось создать директорию!");
                    e.printStackTrace();
                }
            }
        //получаем мапу всех директорий в целевой папке
        Map<Path, Long> directoriesAndFilesDest = directory(destPath);

        //создаем временную мапу полных целевых директорий из исходного с размерами исходных файлов
        Map<Path, Long> newDirectoriesAndFilesDest = new LinkedHashMap<>();
        createNewMapFromSourceWithDistPath(sourcePath, destPath, directoriesAndFilesSource, newDirectoriesAndFilesDest);

        try {
            //проверяем удаленные файлы и папки из исходного списка
            deleteNonExistDirectory(directoriesAndFilesDest, newDirectoriesAndFilesDest);
            //копируем (при необходимости заменяем) файлы и директории
            copyDirectories(newDirectoriesAndFilesDest, sourcePath, destPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void createNewMapFromSourceWithDistPath(Path sourcePath, Path destPath,
                                                           Map<Path, Long> directoriesAndFilesSource,
                                                           Map<Path, Long> newDirectoriesAndFilesDest) {
        for (Map.Entry<Path, Long> next : directoriesAndFilesSource.entrySet()) {
            //получаем Path каждой записи в листе
            Path pathSource = next.getKey();
            Long value = next.getValue();
            //получаем разницу Path
            Path tempSource = sourcePath.relativize(pathSource);
            //соединяем адрес temp+dest
            Path finalPath = destPath.resolve(tempSource);
            newDirectoriesAndFilesDest.put(finalPath, value);
        }
    }

    private static void copyDirectories(Map<Path, Long> newDirectoriesAndFilesDest, Path sourcePath, Path destPath) throws IOException {
        for (Map.Entry<Path, Long> next : newDirectoriesAndFilesDest.entrySet()) {
            Path key = next.getKey();
            //получаем части Path
            Path tempSource1 = destPath.relativize(key);
            //соединяем их в полный адрес
            Path finalPathSource = sourcePath.resolve(tempSource1);
            //если файла не существует
            if (!Files.exists(key)) {
                Files.copy(finalPathSource, key, StandardCopyOption.COPY_ATTRIBUTES);
            } else {
                //если элемент найден, сравниваем размер
                if ((Files.size(finalPathSource) != Files.size(key)) && (!Files.isDirectory(finalPathSource))) {
                    //если не равны - заменяем
                    Files.copy(finalPathSource, key, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    //метод удаляет все файлы и папки из Dest директории, которых нет в исходной папке
    private static void deleteNonExistDirectory(Map<Path, Long> directoriesAndFilesDest, Map<Path, Long> newDirectoriesAndFilesDest) throws IOException {
        //сравниваем целевую папку с временным списком
        Iterator<Map.Entry<Path, Long>> iteratorDirectoriesDest = directoriesAndFilesDest.entrySet().iterator();
        ArrayList<Path> listForDelete = new ArrayList<>();
        //пока в Dest директории есть файлы и папки - итерируемся
        while (iteratorDirectoriesDest.hasNext()) {
            //получаем очередной элемент
            Map.Entry<Path, Long> next = iteratorDirectoriesDest.next();
            Path keyDest = next.getKey();
            Long valueDest = next.getValue();
            boolean flag = false;
            //ищем такой же элемент в новой мапе
            for (Map.Entry<Path, Long> nextNew : newDirectoriesAndFilesDest.entrySet()) {
                Path keyNew = nextNew.getKey();
                if (keyDest.equals(keyNew)) {
                    flag = true;
                }
            }
            //если элемент не найден - удаляем его из Dest папки
            if (!flag) {
                //и добавляем его для удаления из мапы и с Dest папки (после завершения работы итератора)
                listForDelete.add(keyDest);
            }
        }
        //сначала удаляем все файлы
        for (int i = 0; i < listForDelete.size(); i++) {
            Path pathDelete = listForDelete.get(i);
            //если это файл - удаляем
            if (!Files.isDirectory(pathDelete)) {
                Files.delete(pathDelete);
                directoriesAndFilesDest.remove(pathDelete);
                listForDelete.remove(pathDelete);
            }
        }
        //потом пустые папки, начиная с самой глубокой
        //для чего сортируем список по длинне в обратном порядке
        Comparator<Path> pathComparator = (o1, o2) -> {
            int result;
            if (o1.toString().length() == o2.toString().length()) {
                result = 0;
            } else {
                result = (o1.toString().length() > o2.toString().length()) ? 1 : -1;
            }

            return result;
        };
        listForDelete.sort(pathComparator);

        for (int i = listForDelete.size() - 1; i >= 0; i--) {
            Path pathDelete = listForDelete.get(i);
            Files.delete(pathDelete);
            directoriesAndFilesDest.remove(pathDelete);
            listForDelete.remove(pathDelete);
        }
    }

    //метод возвращает список всех файлов и папок в указанной директории с полными путями и размером
    private static Map<Path, Long> directory(Path directory) {
        Map<Path, Long> map = new LinkedHashMap<>();
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(directory)) {
            for (Path path : directoryStream) {
                long size = Files.size(path);
                map.put(path, size);
                if (Files.isDirectory(path)) {
                    map.putAll(directory(path)); //рекурсивный вызов для проверки вложенных папок
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return map;
    }
}
/*
Задача для самостаятельной работы, обсуждение в slack.
Реализовать синхронизацию директорий. Консольная программа, которая на вход
принимает 2 параметра source, dest. Должна состоять из 1 файла
и запускаться java FileSynch. Соответственно, клас должен быть без пакета
(это плохая практика, не повторяйте на своих проектах - ведите структуру пакетов)

Програма должна:
Создать папку dest, если такой нету.
Скопировать в папку dest все файлы из source, которые отсутствуют в dest.
При копировании соблюдать структуру директорий. Например, есть файл source/dir/file.txt.
Если отсутствует dest/dir/file.txt - необходимо его скопировать, так
чтобы после операции появился файли dest/dir/file.txt эквивалентный исходному.
Скопировать все измененные файлы из папки source в папку dest. Для простоты,
изменение содержания файла следует учитывать его размер.
Тоесть, если файл source/f1.txt отличается размером от файла dest/f1.txt - заменить его.
Удалить файл из dest если он отсутствует в source, например, если файл был удален в source.
Для решение следует использовать java.nio.file API которое появилось в java7.
Разбор этого модуля предоставляется на самостоятельное рассмотрение,
все вопросы следует задавать в соответствующем канале.
Решение опубликовать в своем профиле Github, лин на решение опубликовать в канале slack

Внимание!
В канале уже присутствуют разборы предыдущих решений - следует их пересмотреть и
внести соответствующие правки в собственный код. Мы не гарантируем разбор решение каждого учасника,
оно может быть сделаным выборочно.
 */
