import DataAccessObjects.ProductDao;
import Models.Product;
import org.apache.commons.cli.*;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.SimpleDateFormat;
import java.util.*;

public class PriceGrabber {
    private static final Logger logger = Logger.getLogger(PriceGrabber.class);
    private static final String PROGRAM_NAME = "PriceGrabber";
    private static final String IMAGES_FOLDER = "images";
    private static final HelpFormatter FORMATTER = new HelpFormatter();
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMMM yyyy г.");
    private static final List<String> LINKS_TO_PROCESS = new LinkedList<>();
    private static final HashSet<String> PROCESSED_LINKS = new HashSet<>();
    private static final HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();

    static {
        System.setProperty("log4j2.configurationFile", "log4j2.properties");
    }

    public static void main(String[] args) throws Exception {
        Options options = intializeOptions();
        CommandLineParser cmdParser = new DefaultParser();
        CommandLine cmd;
        try {
            cmd = cmdParser.parse(options, args);
        } catch (ParseException e) {
            FORMATTER.printHelp(PROGRAM_NAME, options, true);
            return;
        }

        if (cmd.hasOption("help")) {
            FORMATTER.printHelp(PROGRAM_NAME, options, true);
            return;
        }

        LINKS_TO_PROCESS.add(cmd.getOptionValue("url"));

        ProductDao dao;
        Properties props = new Properties();
        props.load(Resources.getResourceAsReader("properties.properties"));
        String mBatisResource = props.getProperty("mb_resource");
        try (Reader reader = Resources.getResourceAsReader(mBatisResource)) {
            SqlSessionFactory sessionFactory = new SqlSessionFactoryBuilder().build(reader, props);
            dao = new ProductDao(sessionFactory);
        }

        while (LINKS_TO_PROCESS.size() != 0) {
            String link = LINKS_TO_PROCESS.remove(0);
            if (PROCESSED_LINKS.contains(link))
                continue;
            PROCESSED_LINKS.add(link);

            Document page = getDoc(link);
            if (page.location().contains("ad")) {
                Product product = scrapeProduct(page);
                Product existing = dao.getProduct(product.id);
                if (existing != null) {
                    if (existing.price != product.price)
                        logger.log(Level.INFO, String.format("Price changed for product %s: %f -> %f", product.name, existing.price, product.price));
                    if (existing.available && !product.available)
                        logger.log(Level.INFO, String.format("Product %s is no longer available", product.name));
                    dao.updateProduct(product);
                    continue;
                }
                logger.log(Level.INFO, "New product was added in the database! ProductId: " + product.id);
                dao.insertProduct(product);
                continue;
            }
            getProductListings(page);
        }
    }

    private static Options intializeOptions() {
        Options options = new Options();
        Option url = Option.builder("url").argName("URL").hasArg().required().desc("The url to scrape").build();
        options.addOption(url);
        logger.log(Level.DEBUG, "Options initialized");
        return options;
    }

    private static Document getDoc(String link) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder().GET().uri(URI.create(link)).build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        logger.log(Level.DEBUG, "Got response from " + link);
        return Jsoup.parse(response.body(), response.uri().toString());
    }

    private static void getProductListings(Document doc) {
        Element listingsBlock = doc.getElementsByAttributeValue("data-testid", "listing-grid").get(0);
        Elements listings = listingsBlock.getElementsByAttribute("href");
        for (Element listing : listings) {
            String link = listing.attr("abs:href");
            logger.log(Level.DEBUG, "Found link: " + link);
            LINKS_TO_PROCESS.add(link);
        }

        Elements element = doc.getElementsByAttributeValue("data-cy", "pagination-forward");
        if (element.size() != 0) {
            String link = element.get(0).attr("abs:href");
            logger.log(Level.DEBUG, "Found next page: " + link);
            LINKS_TO_PROCESS.add(link);
        }
    }

    private static Product scrapeProduct(Document doc) throws IOException, InterruptedException {
        Product product = new Product();

        product.name = doc.getElementsByAttributeValue("data-cy", "ad_title").get(0).text();

        Element adDescription = doc.getElementsByAttributeValue("data-cy", "ad_description").get(0);
        product.description = adDescription.getElementsByTag("div").get(0).text();

        String text = doc.getElementsByAttributeValue("data-testid", "ad-price-container").get(0).text();
        int indexOfWhiteSpace = text.indexOf(" ");
        product.price = Double.parseDouble(text.substring(0, indexOfWhiteSpace));

        Element footer = doc.getElementsByAttributeValue("data-cy", "ad-footer-bar-section").get(0);
        String id = footer.getElementsByTag("span").get(0).text();
        indexOfWhiteSpace = id.indexOf(" ");
        product.id = Integer.parseInt(id.substring(indexOfWhiteSpace + 1));

        String dateString = doc.getElementsByAttributeValue("data-cy", "ad-posted-at").get(0).text();
        try {
            product.createDate = dateFormat.parse(dateString);
        } catch (Exception e) {
            product.createDate = new Date();
        }

        Element description = doc.getElementsByAttributeValue("data-cy", "ad_description").get(0);
        product.description = description.getElementsByTag("div").get(0).text();

        try {
            Element element = doc.getElementsByClass("swiper-zoom-container").get(0);
            String img = element.getElementsByTag("img").get(0).attr("abs:src");
            product.imagePath = downloadImage(img, product.id);
        } catch (Exception e) {
            product.imagePath = "";
        }

        product.available = true;
        logger.log(Level.INFO, "Scraped product: " + product.name + " with price: " + product.price);

        return product;
    }

    private static String downloadImage(String src, int productId) throws InterruptedException, IOException {
        HttpRequest request = HttpRequest.newBuilder().GET().uri(URI.create(src)).build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        Optional<String> contentTypeOptional = response.headers().firstValue("content-type");
        String fileName = getFileName(productId, contentTypeOptional);

        File file = new File(IMAGES_FOLDER, fileName);
        try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
            fileOutputStream.write(response.body());
        } catch (FileNotFoundException e) {
            System.out.println("Invalid save path: " + file.getPath());
        } catch (Exception e) {
            System.out.println("Couldn't download image: " + file.getName());
        }
        logger.log(Level.DEBUG, "Downloaded image: " + file.getName() + " for product: " + productId);
        return fileName;
    }

    private static String getFileName(int productId, Optional<String> contentTypeOptional) {
        String fileExtension = null;
        if (contentTypeOptional.isPresent()) {
            String s = contentTypeOptional.get();
            int slashIndex = s.lastIndexOf('/') + 1;
            int endIndex = s.lastIndexOf('+');
            if (endIndex == -1)
                endIndex = s.length();

            fileExtension = s.substring(slashIndex, endIndex);
        }
        return fileExtension == null ? String.valueOf(productId) : productId + "." + fileExtension;
    }
}
