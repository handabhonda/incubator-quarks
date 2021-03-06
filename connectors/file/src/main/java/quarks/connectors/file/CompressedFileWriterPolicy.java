/*
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
*/
package quarks.connectors.file;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * A {@link FileWriterPolicy} that generates zip compressed files.
 * <P>
 * {@code CompressedFileWriterPolicy} is used exactly like {@code FileWriterPolicy}.
 * The generated file names are identical to those generated by {@code FileWriterPolicy}
 * except they have a {@code .zip} suffix.
 * </P>
 * <P>
 * The active file is uncompressed.  
 * It is compressed when cycled per the {@link FileWriterCycleConfig}.
 * Hence, a {@link FileWriterCycleConfig#newFileSizeBasedConfig(long) file size based}
 * cycle config specifies the size of the uncompressed active file.
 * </P>
 * <P>
 * An {@link FileWriterRetentionConfig#newAggregateFileSizeBasedConfig(long) aggregate
 * file size} based retention config specifies the total size of the
 * retained compressed files.
 * </P>
 * Sample use:
 * <pre>{@code
 * // Create a CompressedFileWriterPolicy with the configuration:
 * // no explicit flush; cycle the active file when it exceeds 200Kb;
 * // retain up to 1Mb of compressed files.
 * IFileWriterPolicy<String> policy = new CompressedFileWriterPolicy(
 *     FileWriterFlushConfig.newImplicitConfig(),
 *     FileWriterCycleConfig.newFileSizeBasedConfig(200_000),
 *     FileWriterRetentionConfig.newAggregateFileSizeBasedConfig(1_000_000));
 * String basePathname = "/some/directory/and_base_name";
 * 
 * TStream<String> streamToWrite = ...
 * FileStreams.textFileWriter(streamToWrite, () -> basePathname, () -> policy)
 * }</pre>
 *
 * @param <T> stream tuple type
 */
public class CompressedFileWriterPolicy<T> extends FileWriterPolicy<T> {
  
  private final static String SUFFIX = ".zip";
  private final static int BUFSIZE = 8192;

  public CompressedFileWriterPolicy() {
    super();
  }
  
  public CompressedFileWriterPolicy(FileWriterFlushConfig<T> flushConfig,
      FileWriterCycleConfig<T> cycleConfig,
      FileWriterRetentionConfig retentionConfig) {
    super(flushConfig, cycleConfig, retentionConfig);
  }

  @Override
  protected Path hookGenerateFinalFilePath(Path path) {
    // finalPath = the normal finalPath + SUFFIX
    Path finalPath = super.hookGenerateFinalFilePath(path);
    finalPath = finalPath.getParent().resolve(finalPath.getFileName() + SUFFIX);
    return finalPath;
  }

  @Override
  protected void hookRenameFile(Path activePath, Path finalPath) throws IOException {
    // compress into finalPath instead of simple rename
    assert finalPath.toString().endsWith(SUFFIX) : finalPath.toString();
    compressFile(activePath, finalPath);
    activePath.toFile().delete();
  }
  
  protected void compressFile(Path src, Path dst) throws IOException {
    try (
        BufferedInputStream in = new BufferedInputStream(
                new FileInputStream(src.toFile()), BUFSIZE);
        ZipOutputStream out = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(dst.toFile())));
        )
    {
      // zip file entry name is "dst" minus the suffix.
      String dstFileName = dst.getFileName().toString();
      String entryName = dstFileName.substring(0, dstFileName.length() - SUFFIX.length());
      
      out.putNextEntry(new ZipEntry(entryName));
      byte[] data = new byte[BUFSIZE];
      int count;
      while ((count = in.read(data, 0, BUFSIZE)) != -1) {
        out.write(data, 0, count);
      }
    }
    
  }

}
