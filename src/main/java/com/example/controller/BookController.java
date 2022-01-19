package com.example.controller;

import com.example.dao.BookRepository;
import com.example.model.Book;
import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.*;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.*;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
     * @param book { "id":"1" ,"name":"hello","price":80 }
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

//    // 调用报错
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

    /**
     * 通过ElasticsearchRepository来查询所有数据
     * @return
     */
    @GetMapping("/book/findAll")
    public List<Book> findAll() {
        List<Book> list = new ArrayList<>();
        Iterable<Book> books = bookRepository.findAll();
        books.forEach(book -> list.add(book));
        return list;
    }

    /**
     * 通过elasticsearchOperations来查询所有数据
     * NativeSearchQueryBuilder：Spring提供的一个查询条件构建器，帮助构建json格式的请求体
     * @return
     */
    @GetMapping("/book/searchAll")
    public List<Book> searchAll() {
        NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder();
        SearchHits<Book> search = elasticsearchOperations.search(queryBuilder.build(),Book.class);
        List<Book> list = search.getSearchHits().stream().map(item -> item.getContent()).collect(Collectors.toList());
        return list;
    }

    /**
     * 根据唯_id获取数据
     * @param id
     * @return
     */
    @GetMapping("/book/{id}")
    public Book get(@PathVariable("id") String id) {
        // 通过bookRepository来获取指定条件数据
        Optional<Book> optionalBook = bookRepository.findById(id);
        System.out.println("findById: " + optionalBook.get().toString());

        // 构建查询条件
        NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder();
        // 添加基本的分词查询
        queryBuilder.withQuery(QueryBuilders.matchQuery("id", id));
        SearchHit<Book> search = elasticsearchOperations.searchOne(queryBuilder.build(),Book.class);
        System.out.println("searchOne: " + search.getContent().toString());

        Book book = elasticsearchOperations.get(id, Book.class);
//        还可以指定其它索引库
//        Book book = elasticsearchOperations.get(id, Book.class, IndexCoordinates.of("books"));
        return book;
    }

    /**
     * 单条件匹配查询
     * {id='2', name='匹配查询name81', price=81}
     * {id='3', name='匹配查询name82', price=82}
     * {id='4', name='匹配81', price=80}
     * @return
     */
    @GetMapping("/book/query/name")
    public List<Book> getByName() {
        String name = "匹配查询name";
        String termName = "匹配查询name82";

        NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder();
        // match类型查询并不是全匹配,比如此处会把三条记录都查出来
        queryBuilder.withQuery(QueryBuilders.matchQuery("name", name));

        SearchHits<Book> searchs = elasticsearchOperations.search(queryBuilder.build(),Book.class);
        List<Book> list = searchs.getSearchHits().stream().map(item -> item.getContent()).collect(Collectors.toList());
        System.out.println("matchQuerySearch: " + list);

        // 默认取第一个
        SearchHit<Book> search = elasticsearchOperations.searchOne(queryBuilder.build(),Book.class);
        System.out.println("matchQuerySearchOne: " + search.getContent().toString());

        // term类型查询是精确查询
        NativeSearchQueryBuilder termQueryBuilder = new NativeSearchQueryBuilder();
        termQueryBuilder.withQuery(QueryBuilders.termQuery("name", termName));
        searchs = elasticsearchOperations.search(termQueryBuilder.build(),Book.class);
        list = searchs.getSearchHits().stream().map(item -> item.getContent()).collect(Collectors.toList());
        System.out.println("termQuerySearch: " + list);

        // CriteriaQuery精确词条匹配
        Criteria criteria = new Criteria("name").is(termName);
        Query criteriaQuery = new CriteriaQuery(criteria);
        searchs = elasticsearchOperations.search(criteriaQuery, Book.class);
        list = searchs.getSearchHits().stream().map(item -> item.getContent()).collect(Collectors.toList());
        System.out.println("CriteriaQuerySearch: " + list);
        return list;
    }

    /**
     * 多字段查询：从多个字段中检索含有关键字的数据
     * @return
     */
    @GetMapping("/book/query")
    public List<Book> getByNameAndPrice() {
        NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder();
        // multiMatchQuery多字段查询，查询name、price任一字段中含有81的数据
        queryBuilder.withQuery(QueryBuilders.multiMatchQuery("81", "name","price"));
        SearchHits<Book> searches = elasticsearchOperations.search(queryBuilder.build(),Book.class);
        List<Book> list = searches.getSearchHits().stream().map(item -> item.getContent()).collect(Collectors.toList());
        System.out.println("multiMatchQuerySearch: " + list);

        return list;
    }

    // --------------------------------------------------------------------------------------- //


    /**
     * 通过ElasticsearchRepository自定义方法实现多查询条件查询
     *
     * @return
     */
    @GetMapping("/book/query/name/price")
    public List<Book> query() {
        List<Book> result = bookRepository.findByNameAndPrice("匹配查询name82", 82);
        return result;
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
