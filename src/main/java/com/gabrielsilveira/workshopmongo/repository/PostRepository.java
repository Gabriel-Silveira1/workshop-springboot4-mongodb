package com.gabrielsilveira.workshopmongo.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.gabrielsilveira.workshopmongo.domain.Post;

public interface PostRepository extends MongoRepository<Post, String>{
	
}
