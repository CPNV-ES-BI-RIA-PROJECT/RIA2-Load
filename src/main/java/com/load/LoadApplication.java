package com.load;

import com.load.config.DotenvInitializer;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class LoadApplication {

  public static void main(String[] args) {
    new SpringApplicationBuilder(LoadApplication.class)
        .initializers(new DotenvInitializer())
        .run(args);
  }
}
