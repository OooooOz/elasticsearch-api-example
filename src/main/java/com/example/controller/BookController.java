package com.example.controller;

import com.example.dao.BookRepository;
import com.example.model.Book;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.*;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
public class BookController {

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Autowired
    private BookRepository bookRepository;

    /**
     * 手动创建user索引
     * @return
     */
    @GetMapping("/index/user/create")
    public String createIndexUser() {
        IndexOperations indexOperations = elasticsearchOperations.indexOps(IndexCoordinates.of("user"));
        indexOperations.create();
        return "CREATE INDEX USER SUCCESS";
    }

    /**
     * Book实体类已经创建有映射，对应索引books，调用save方法会保持到对应索引下
     * 已有数据会更新
     *
     * @param book { "id":"2" ,"name":"hello","price":80 }
     * @return
     */
    @PostMapping("/book/save")
    public String save(@RequestBody Book book) {
        Book bookTemp = elasticsearchOperations.save(book);
        return bookTemp.getId();
    }

    /**
     * 根据索引条件（id、实体类）在指定索引库保存/更新数据
     * @param book
     * @return hits中的_id，非javabean中的id
     */
    @PostMapping("/book/index")
    public String index(@RequestBody Book book){
        IndexQuery indexQuery= new IndexQueryBuilder()
                .withId(book.getId())
                .withObject(book)
                .build();

        return elasticsearchOperations.index(indexQuery, IndexCoordinates.of("books"));
    }

//    调用报错
//    @PostMapping("/book/update")
//    public String update(@RequestBody Book book) {
//        Criteria criteria = new Criteria("id").is(book.getId());
//        Query criteriaQuery = new CriteriaQuery(criteria);
//        UpdateQuery updateQuery = UpdateQuery.builder(criteriaQuery).withScript(book.getId()).build();
//        UpdateResponse updateResponse = elasticsearchOperations.update(updateQuery, IndexCoordinates.of("books"));
//        return updateResponse.getResult().toString();
//    }

    /**
     * 根据索引_id删除数据
     * @param id
     * @return 删除的索引_id
     */
    @DeleteMapping("/book/delete/{id}")
    public String delete(@PathVariable("id") String id){
        return elasticsearchOperations.delete(id, IndexCoordinates.of("books"));
    }

    /**
     * 根据实体类数据删除数据
     * @param book
     * @return 删除的索引_id
     */
    @DeleteMapping("/book/delete/entity")
    public String delete(@RequestBody Book book){
        return elasticsearchOperations.delete(book, IndexCoordinates.of("books"));
    }


    // --------------------------------------------------------------------------------------- //


    /**
     * curl -X GET http://localhost:8080/book/1
     *
     * @param id
     * @return
     */
    @GetMapping("/book/{id}")
    public Book findById(@PathVariable("id") String id) {
        Book book = elasticsearchOperations.get(id, Book.class, IndexCoordinates.of("books"));
        return book;
    }

    /**
     * curl -X GET 'http://localhost:8080/query/hello'
     *
     * @param name
     * @return
     */
    @GetMapping("/query/{name}")
    public List<Book> query(@PathVariable("name") String name) {
        List<Book> result = bookRepository.findByNameAndPrice(name, 80);
        return result;
    }

    /**
     * curl -X GET 'http://localhost:8080/search/hello'
     *
     * @param name
     * @return
     */
    @GetMapping("/search/{name}")
    public List<Book> search(@PathVariable("name") String name) {
        // Criteria criteria = new Criteria("name").is("hello").and("price").greaterThan(30);
        Criteria criteria = new Criteria("name").is(name);
        Query criteriaQuery = new CriteriaQuery(criteria);
        SearchHits<Book> search = elasticsearchOperations.search(criteriaQuery, Book.class);
        List<Book> list = search.getSearchHits().stream().map(item -> item.getContent()).collect(Collectors.toList());
        return list;
    }

    /**
     * curl http://localhost:8080/page/hello
     *
     * @param name
     * @return
     */
    @GetMapping("/page/{name}")
    public Page<Book> page(@PathVariable("name") String name) {
        Criteria criteria = new Criteria("name").is(name);
        Query criteriaQuery = new CriteriaQuery(criteria);
        SearchHits<Book> searchHits = elasticsearchOperations.search(criteriaQuery, Book.class);
        SearchPage<Book> searchPage = SearchHitSupport.searchPageFor(searchHits, Pageable.ofSize(1));
        Page<Book> page = (Page<Book>) SearchHitSupport.unwrapSearchHits(searchPage);
        // 总页数
        int totalPages = page.getTotalPages();
        // 总记录数
        long totalElements = page.getTotalElements();
        // 每页显示多少条
        int size = page.getSize();
        return page;
    }
}
