package com.gregbarasch.lamportclocks.model;

import org.apache.log4j.Logger;

public class Resource {

    private static final Logger logger = Logger.getLogger(Resource.class);

    public static void acquire() {
        logger.info("Resource acquired");
    }

    public static void release() {
        logger.info("Resource released");
    }
}
