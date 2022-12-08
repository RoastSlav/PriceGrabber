package DataAccessObjects;

import Mappers.ProductMapper;
import Models.Product;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

public class ProductDao implements ProductMapper {
    SqlSessionFactory sqlFactory;

    public ProductDao(SqlSessionFactory sqlFactory) {
        this.sqlFactory = sqlFactory;
    }

    @Override
    public Product getProduct(int id) {
        validateNotNegative(id);
        try (SqlSession session = sqlFactory.openSession(true)) {
            ProductMapper mapper = session.getMapper(ProductMapper.class);
            return mapper.getProduct(id);
        }
    }

    @Override
    public Product[] getAllProducts() {
        try (SqlSession session = sqlFactory.openSession(true)) {
            ProductMapper mapper = session.getMapper(ProductMapper.class);
            return mapper.getAllProducts();
        }
    }

    @Override
    public int insertProduct(Product product) {
        validateNotNull(product);
        try (SqlSession session = sqlFactory.openSession(true)) {
            ProductMapper mapper = session.getMapper(ProductMapper.class);
            return mapper.insertProduct(product);
        }
    }

    @Override
    public int updateProduct(Product product) {
        validateNotNull(product);
        try (SqlSession session = sqlFactory.openSession(true)) {
            ProductMapper mapper = session.getMapper(ProductMapper.class);
            return mapper.updateProduct(product);
        }
    }

    private void validateNotNegative(int id) {
        if (id < 0)
            throw new IllegalArgumentException("Parameter must be positive");
    }

    private void validateNotNull(Object obj) {
        if (obj == null)
            throw new IllegalArgumentException("Object can't be a null value");
    }
}
