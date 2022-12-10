package memory.classloader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MemoryClassLoader extends ClassLoader {

	private final byte[] jarBytes;
	private final Set<String> names;

	public MemoryClassLoader(ClassLoader parent, byte[] jarBytes) throws IOException {
		super(parent);
		this.jarBytes = jarBytes;
		this.names = loadNames(jarBytes);
	}

	static private byte[] entryDataBytes(InputStream inputStream) throws IOException {
		byte[] data = new byte[0];
		try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();) {
			byte[] buffer = new byte[1024];

			for (int bytesRead = 0; (bytesRead = inputStream.read(buffer, 0, buffer.length)) != -1;) {
				outputStream.write(buffer, 0, bytesRead);
			}
			outputStream.flush();
			data = outputStream.toByteArray();
			inputStream.close();
		}
		return data;
	}

	@Override
	public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		Class<?> clazz = findLoadedClass(name);
		if (clazz == null) {
			try {
				InputStream in = getResourceAsStream(name.replace('.', '/') + ".class");
				byte[] bytes = entryDataBytes(in);
				clazz = defineClass(name, bytes, 0, bytes.length);
				if (resolve) {
					resolveClass(clazz);
				}
			} catch (Exception e) {
				clazz = super.loadClass(name, resolve);
			}
		}
		return clazz;
	}

	@Override
	public InputStream getResourceAsStream(String name) {
		// Check first if the entry name is known
		if (!names.contains(name)) {
			return super.getResourceAsStream(name);
		}
		// I moved the ZipInputStream declaration outside the
		// try-with-resources statement as it must not be closed otherwise
		// the returned InputStream won't be readable as already closed
		boolean found = false;
		ZipInputStream zis = null;
		try {
			zis = new ZipInputStream(new ByteArrayInputStream(jarBytes));
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				if (entry.getName().equals(name)) {
					found = true;
					return zis;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			// Only close the stream if the entry could not be found
			if (zis != null && !found) {
				try {
					zis.close();
				} catch (IOException e) {
					// ignore me
				}
			}
		}
		return null;
	}

	/**
	 * This will put all the entries into a thread-safe Set
	 */
	private static Set<String> loadNames(byte[] jarBytes) throws IOException {
		Set<String> set = new HashSet<>();
		try (ZipInputStream jis = new ZipInputStream(new ByteArrayInputStream(jarBytes))) {
			ZipEntry entry;
			while ((entry = jis.getNextEntry()) != null) {
				set.add(entry.getName());
			}
		}
		return Collections.unmodifiableSet(set);
	}

}
