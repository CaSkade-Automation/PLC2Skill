package plc2skill.test;


import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

import plc2skill.mapping.Plc2SkillMapper;

class MappingTest {


	@Test
	void testMapping() throws IOException {
		Path mappingPath = Paths.get("src", "test", "resources", "Test-PlcOpenXml.xml").toAbsolutePath();
		String endpointUrl = "opc.tcp://localhost:4840";
		String nodeIdRoot = "|var|CODESYS Control Win V3 x64";
		Plc2SkillMapper mapper = new Plc2SkillMapper.Builder(mappingPath, endpointUrl)
				.setNodeIdRoot(nodeIdRoot)
				.build();
		
		String actualResult = mapper.executeMapping();
		byte[] fileFromBytes = Files.readAllBytes(Paths.get("src", "test", "resources", "ExpectedMappingOutput.ttl"));
		String expectedResult = new String(fileFromBytes, StandardCharsets.UTF_8);
		
		assertThat(actualResult).isEqualToIgnoringWhitespace(expectedResult);
	}

}
