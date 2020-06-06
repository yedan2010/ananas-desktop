package org.ananas.runner.core.extension;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;
import org.ananas.runner.core.StepRunner;
import org.ananas.runner.core.paginate.AutoDetectedSchemaPaginator;
import org.ananas.runner.steprunner.DefaultDataViewer;
import org.ananas.runner.steprunner.api.APIConnector;
import org.ananas.runner.steprunner.api.APIPaginator;
import org.ananas.runner.steprunner.files.FileLoader;
import org.ananas.runner.steprunner.files.csv.CSVConnector;
import org.ananas.runner.steprunner.files.csv.CSVPaginator;
import org.ananas.runner.steprunner.files.excel.ExcelConnector;
import org.ananas.runner.steprunner.files.excel.ExcelPaginator;
import org.ananas.runner.steprunner.files.json.JsonConnector;
import org.ananas.runner.steprunner.files.json.JsonPaginator;
import org.ananas.runner.steprunner.gcs.BigQueryConnector;
import org.ananas.runner.steprunner.gcs.BigQueryLoader;
import org.ananas.runner.steprunner.gcs.BigqueryPaginator;
import org.ananas.runner.steprunner.gcs.GCSConnector;
import org.ananas.runner.steprunner.gcs.GCSLoader;
import org.ananas.runner.steprunner.gcs.GCSPaginator;
import org.ananas.runner.steprunner.jdbc.JdbcConnector;
import org.ananas.runner.steprunner.jdbc.JdbcLoader;
import org.ananas.runner.steprunner.jdbc.JdbcPaginator;
import org.ananas.runner.steprunner.mongo.MongoDBConnector;
import org.ananas.runner.steprunner.mongo.MongoDBLoader;
import org.ananas.runner.steprunner.mongo.MongoDBPaginator;
import org.ananas.runner.steprunner.sql.SQLTransformer;

public class ExtensionRegistry {
  private static final Map<String, Class<? extends StepRunner>> STEP_REGISTRY = new HashMap<>();
  private static final Map<String, Class<? extends AutoDetectedSchemaPaginator>>
      PAGINATOR_REGISTRY = new HashMap<>();

  private static final String STEP_RUNNER_CLASS_NAME = "StepRunner";
  private static final String PAGINATOR_CLASS_NAME = "Paginator";

  public static boolean hasStep(String metaId, ExtensionManager externalSource) {
    if (STEP_REGISTRY.containsKey(metaId)) {
      return true;
    }
    if (externalSource != null && externalSource.hasStepMetadata(metaId)) {
      return true;
    }
    return false;
  }

  public static Class<? extends StepRunner> getStep(
      String metaId, boolean local, ExtensionManager externalSource) {
    // 1. find the class in internal step registry
    Class<? extends StepRunner> clazz = STEP_REGISTRY.get(metaId);
    if (clazz != null) {
      return clazz;
    }

    // 2. if not found, search it from classpath
    URL[] additionalClasspath = new URL[] {};

    // For local runner only, get additional classpath from local extension
    // A remote runner will have all jars uploaded to workers through filesToStage
    // or other parameters, and they are already in classpath on workers
    if (local && externalSource != null && externalSource.hasStepMetadata(metaId)) {
      StepMetadata meta = externalSource.getStepMetadata(metaId);
      additionalClasspath = (URL[]) meta.classpath.toArray(new URL[meta.classpath.size()]);
    }

    // search the StepRunner class from the classpath
    try {
      URLClassLoader classLoader = URLClassLoader.newInstance(additionalClasspath);
      return (Class<? extends StepRunner>)
          classLoader.loadClass(metaId + "." + STEP_RUNNER_CLASS_NAME);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  public static boolean hasPaginator(String metaId, ExtensionManager extensionManager) {
    if (PAGINATOR_REGISTRY.containsKey(metaId)) {
      return true;
    }

    if (extensionManager != null && extensionManager.hasStepMetadata(metaId)) {
      StepMetadata meta = extensionManager.getStepMetadata(metaId);
      return meta.type.equals("Source")
          || meta.type.equals("Destination")
          || meta.type.equals("Visualization");
    }

    return false;
  }

  public static Class<? extends AutoDetectedSchemaPaginator> getPaginator(
      String metaId, ExtensionManager extensionManager) {
    // 1. find the class in internal step registry
    Class<? extends AutoDetectedSchemaPaginator> clazz = PAGINATOR_REGISTRY.get(metaId);
    if (clazz != null) {
      return clazz;
    }

    // 2. if not found, search it from classpath
    URL[] additionalClasspath = new URL[] {};

    // paginator always search extension classpath
    if (extensionManager != null && extensionManager.hasStepMetadata(metaId)) {
      StepMetadata meta = extensionManager.getStepMetadata(metaId);
      additionalClasspath = (URL[]) meta.classpath.toArray();
    }

    // search the Paginator class from the classpath
    try {
      URLClassLoader classLoader = URLClassLoader.newInstance(additionalClasspath);
      return (Class<? extends AutoDetectedSchemaPaginator>)
          classLoader.loadClass(metaId + "." + PAGINATOR_CLASS_NAME);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  public static void registerStep(String metaId, Class<? extends StepRunner> clazz) {
    STEP_REGISTRY.put(metaId, clazz);
  }

  public static void registerPaginator(
      String metadataId, Class<? extends AutoDetectedSchemaPaginator> paginatorClass) {
    PAGINATOR_REGISTRY.put(metadataId, paginatorClass);
  }

  public static void registerConnector(
      String metaId,
      Class<? extends StepRunner> stepRunnerClass,
      Class<? extends AutoDetectedSchemaPaginator> paginatorClass) {
    registerStep(metaId, stepRunnerClass);
    registerPaginator(metaId, paginatorClass);
  }

  public static void registerTransformer(
      String metaId, Class<? extends StepRunner> stepRunnerClass) {
    registerStep(metaId, stepRunnerClass);
  }

  public static void registerLoader(
      String metaId,
      Class<? extends StepRunner> stepRunnerClass,
      Class<? extends AutoDetectedSchemaPaginator> paginatorClass) {
    registerStep(metaId, stepRunnerClass);
    registerPaginator(metaId, paginatorClass);
  }

  public static void registerViewer(String metaId, Class<? extends StepRunner> stepRunnerClass) {
    registerStep(metaId, stepRunnerClass);
  }

  public static void init() {
    ExtensionRegistry.registerConnector(
        "org.ananas.source.file.csv", CSVConnector.class, CSVPaginator.class);
    ExtensionRegistry.registerConnector(
        "org.ananas.source.file.json", JsonConnector.class, JsonPaginator.class);
    ExtensionRegistry.registerConnector(
        "org.ananas.source.file.gcs", GCSConnector.class, GCSPaginator.class);
    ExtensionRegistry.registerConnector(
        "org.ananas.source.gcp.bigquery", BigQueryConnector.class, BigqueryPaginator.class);
    ExtensionRegistry.registerConnector(
        "org.ananas.source.jdbc.mysql", JdbcConnector.class, JdbcPaginator.class);
    ExtensionRegistry.registerConnector(
        "org.ananas.source.jdbc.postgres", JdbcConnector.class, JdbcPaginator.class);
    ExtensionRegistry.registerConnector(
        "org.ananas.source.api", APIConnector.class, APIPaginator.class);
    ExtensionRegistry.registerConnector(
        "org.ananas.source.file.excel", ExcelConnector.class, ExcelPaginator.class);
    ExtensionRegistry.registerConnector(
        "org.ananas.source.nosql.mongodb", MongoDBConnector.class, MongoDBPaginator.class);

    ExtensionRegistry.registerTransformer("org.ananas.transform.sql", SQLTransformer.class);

    ExtensionRegistry.registerLoader(
        "org.ananas.destination.file.csv", FileLoader.class, CSVPaginator.class);
    ExtensionRegistry.registerLoader(
        "org.ananas.destination.jdbc.mysql", JdbcLoader.class, JdbcPaginator.class);
    ExtensionRegistry.registerLoader(
        "org.ananas.destination.jdbc.postgres", JdbcLoader.class, JdbcPaginator.class);
    ExtensionRegistry.registerLoader(
        "org.ananas.destination.gcp.gcs", GCSLoader.class, GCSPaginator.class);
    ExtensionRegistry.registerLoader(
        "org.ananas.destination.gcp.bigquery", BigQueryLoader.class, BigqueryPaginator.class);
    ExtensionRegistry.registerLoader(
        "org.ananas.destination.nosql.mongodb", MongoDBLoader.class, MongoDBPaginator.class);

    ExtensionRegistry.registerViewer("org.ananas.visualization.barchart", DefaultDataViewer.class);
    ExtensionRegistry.registerViewer("org.ananas.visualization.piechart", DefaultDataViewer.class);
    ExtensionRegistry.registerViewer("org.ananas.visualization.linechart", DefaultDataViewer.class);
    ExtensionRegistry.registerViewer("org.ananas.visualization.bignumber", DefaultDataViewer.class);
  }
}