package Mappers;

import Models.Product;

public interface ProductMapper {
    public Product getProduct(int id);
    public Product[] getAllProducts();
    public int insertProduct(Product product);
    public int updateProduct(Product product);
}
