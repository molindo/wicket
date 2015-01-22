/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.wicket.protocol.http;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.wicket.Application;
import org.apache.wicket.IPageMap;
import org.apache.wicket.MetaDataKey;
import org.apache.wicket.Page;
import org.apache.wicket.PageMap;
import org.apache.wicket.Session;
import org.apache.wicket.WicketRuntimeException;
import org.apache.wicket.protocol.http.pagestore.DiskPageStore;

/**
 * a session store based on {@link SecondLevelCacheSessionStore} that replaces the lastPage of idle
 * sessions
 */
public class ExpiringSecondLevelCacheSessionStore extends SecondLevelCacheSessionStore
{

	private static final int DEFAULT_TIMEOUT_MS = 10 * 60 * 1000;
	private static final MetaDataKey<IdlePageMaps> IDLE_PAGE_MAPS = new MetaDataKey<IdlePageMaps>()
	{
		private static final long serialVersionUID = 1L;
	};

	private static IdlePageMaps getIdlePageMaps()
	{
		IdlePageMaps maps = Application.get().getMetaData(IDLE_PAGE_MAPS);
		if (maps == null)
		{
			throw new WicketRuntimeException("IdlePageMaps not set");
		}
		return maps;
	}

	/**
	 * 
	 * Construct. uses a DiskPageStore and a default timeout of 10 minutes
	 * 
	 * @param application
	 *            The application for this store
	 * @see #ExpiringSecondLevelCachePageMap(String, Application, String)
	 * @see DiskPageStore#DiskPageStore()
	 */
	public ExpiringSecondLevelCacheSessionStore(final Application application)
	{
		this(application, new DiskPageStore(), DEFAULT_TIMEOUT_MS);
	}

	/**
	 * 
	 * Construct. uses a DiskPageStore
	 * 
	 * @param application
	 *            The application for this store
	 * @param idleTimeoutMs
	 *            the timeout in milliseconds after which a {@link PageMap} is considered idle
	 * @see #ExpiringSecondLevelCachePageMap(String, Application, String)
	 * @see DiskPageStore#DiskPageStore()
	 */
	public ExpiringSecondLevelCacheSessionStore(final Application application, long idleTimeoutMs)
	{
		this(application, new DiskPageStore(), idleTimeoutMs);
	}

	/**
	 * 
	 * Construct. uses a default timeout of 10 minutes
	 * 
	 * @param application
	 *            The application for this store
	 * @param pageStore
	 *            Page store for keeping page versions
	 * @see #ExpiringSecondLevelCachePageMap(String, Application, String)
	 */
	public ExpiringSecondLevelCacheSessionStore(final Application application,
		final IPageStore pageStore)
	{
		this(application, pageStore, DEFAULT_TIMEOUT_MS);
	}

	/**
	 * 
	 * Construct.
	 * 
	 * @param application
	 *            The application for this store
	 * @param pageStore
	 *            Page store for keeping page versions
	 * @param idleTimeoutMs
	 *            the timeout in milliseconds after which a {@link PageMap} is considered idle
	 */
	public ExpiringSecondLevelCacheSessionStore(final Application application,
		final IPageStore pageStore, final long idleTimeoutMs)
	{
		super(application, pageStore);

		application.setMetaData(IDLE_PAGE_MAPS, new IdlePageMaps(idleTimeoutMs));
	}

	@Override
	public IPageMap createPageMap(final String name)
	{
		return new ExpiringSecondLevelCachePageMap(Session.get().getId(), Application.get(), name);
	}

	static class ExpiringSecondLevelCachePageMap extends
		SecondLevelCacheSessionStore.SecondLevelCachePageMap
	{
		private static final long serialVersionUID = 1L;

		ExpiringSecondLevelCachePageMap(final String sessionId, final Application application,
			final String name)
		{
			super(sessionId, application, name);
		}

		@Override
		protected void onAfterLastPageSet(final Page lastPage)
		{
			if (lastPage != null)
			{
				getIdlePageMaps().touch(this);
			}
		}
	}

	private static final class IdlePageMaps
	{

		private static final int DELAY = 1;
		private static final TimeUnit DELAY_UNIT = TimeUnit.MINUTES;

		private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

		private final LinkedHashMap<PageMapKey, Long> map = new LinkedHashMap<PageMapKey, Long>();
		private final long idleTimeoutMs;

		public IdlePageMaps(final long idleTimeoutMs)
		{
			this.idleTimeoutMs = idleTimeoutMs;

			executor.schedule(new Callable<Void>()
			{

				public Void call() throws Exception
				{
					try
					{
						synchronized (map)
						{
							final Iterator<Map.Entry<PageMapKey, Long>> iter = map.entrySet()
								.iterator();
							while (iter.hasNext())
							{
								final Map.Entry<PageMapKey, Long> e = iter.next();
								if (isIdle(e.getValue()))
								{
									SecondLevelCachePageMap pageMap = e.getKey().getPageMap();
									if (pageMap != null)
									{
										pageMap.unsetLastPage();
									}
									iter.remove();
								}
								else
								{
									// entries are ordered, break after first non-idle
									break;
								}
							}
						}

					}
					finally
					{
						// reschedule
						executor.schedule(this, DELAY, DELAY_UNIT);
					}
					return null;
				}
			}, DELAY, DELAY_UNIT);
		}

		protected boolean isIdle(final Long value)
		{
			return value == null || value < System.currentTimeMillis() - idleTimeoutMs;
		}

		public void touch(final SecondLevelCachePageMap pageMap)
		{
			PageMapKey key = new PageMapKey(pageMap);
			synchronized (map)
			{
				map.remove(key);
				map.put(key, System.currentTimeMillis());
			}
		}
	}

	private static final class PageMapKey
	{
		private final WeakReference<SecondLevelCachePageMap> pageMapRef;
		private final String sessionId;
		private final String name;

		PageMapKey(SecondLevelCachePageMap pageMap)
		{
			name = pageMap.getName();
			sessionId = pageMap.getSession().getId();
			pageMapRef = new WeakReference<SecondLevelCachePageMap>(pageMap);
		}

		public SecondLevelCachePageMap getPageMap()
		{
			return pageMapRef.get();
		}

		@Override
		public int hashCode()
		{
			final int prime = 31;
			int result = 1;
			result = prime * result + ((name == null) ? 0 : name.hashCode());
			result = prime * result + ((sessionId == null) ? 0 : sessionId.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj)
		{
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			PageMapKey other = (PageMapKey)obj;
			if (name == null)
			{
				if (other.name != null)
					return false;
			}
			else if (!name.equals(other.name))
				return false;
			if (sessionId == null)
			{
				if (other.sessionId != null)
					return false;
			}
			else if (!sessionId.equals(other.sessionId))
				return false;
			return true;
		}

		@Override
		public String toString()
		{
			return "PageMapKey [name=" + name + ", sessionId=" + sessionId + "]";
		}
	}
}
