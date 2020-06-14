/*
 * Copyright 2019 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.benchmarks.aeron.remote;

import io.aeron.archive.Archive;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.status.SystemCounterDescriptor;
import org.agrona.CloseHelper;
import org.agrona.ErrorHandler;
import org.agrona.concurrent.status.AtomicCounter;

final class ArchivingMediaDriver implements AutoCloseable
{
    private final MediaDriver driver;
    private final Archive archive;

    private ArchivingMediaDriver(final MediaDriver driver, final Archive archive)
    {
        this.driver = driver;
        this.archive = archive;
    }

    public void close() throws Exception
    {
        CloseHelper.closeAll(archive, driver);
        archive.context().deleteDirectory();
        if (null != driver)
        {
            driver.context().deleteDirectory();
        }
    }

    static ArchivingMediaDriver launchArchiveWithStandaloneDriver()
    {
        return new ArchivingMediaDriver(null, Archive.launch(new Archive.Context().deleteArchiveOnStart(true)));
    }

    static ArchivingMediaDriver launchArchiveWithEmbeddedDriver()
    {
        MediaDriver driver = null;
        Archive archive = null;
        try
        {
            final MediaDriver.Context driverCtx = new MediaDriver.Context()
                .dirDeleteOnStart(true)
                .spiesSimulateConnection(true);

            driver = MediaDriver.launch(driverCtx);

            final Archive.Context archiveCtx = new Archive.Context()
                .aeronDirectoryName(driverCtx.aeronDirectoryName())
                .deleteArchiveOnStart(true);

            final int errorCounterId = SystemCounterDescriptor.ERRORS.id();
            final AtomicCounter errorCounter = null == archiveCtx.errorCounter() ?
                new AtomicCounter(driverCtx.countersValuesBuffer(), errorCounterId) : archiveCtx.errorCounter();

            final ErrorHandler errorHandler = null == archiveCtx.errorHandler() ?
                driverCtx.errorHandler() : archiveCtx.errorHandler();

            archive = Archive.launch(archiveCtx
                .mediaDriverAgentInvoker(driver.sharedAgentInvoker())
                .aeronDirectoryName(driverCtx.aeronDirectoryName())
                .errorHandler(errorHandler)
                .errorCounter(errorCounter));
            return new ArchivingMediaDriver(driver, archive);
        }
        catch (final Exception ex)
        {
            CloseHelper.quietCloseAll(archive, driver);
            throw ex;
        }
    }
}
